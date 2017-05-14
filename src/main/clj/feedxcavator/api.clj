;; Feedxcavator (a HTML to RSS converter)
;; (C) 2011 g/christensen (gchristnsn@gmail.com)

(ns feedxcavator.api
  (:import [com.google.appengine.api.memcache Expiration]
           [java.net URLEncoder]
           org.apache.commons.codec.binary.Base64
           java.security.MessageDigest
           java.math.BigInteger
           java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream)
  (:require [appengine-magic.core :as ae]
            [appengine-magic.services.user :as user]
            [appengine-magic.services.task-queues :as queue]
            [appengine-magic.services.memcache :as cache]
            [net.cgrand.enlive-html :as enlive]
            [clojure.string :as str]
            [clj-time.core :as tm])
  (:use clojure.tools.macro
        clojure.walk
        [appengine-magic.services.url-fetch :only [fetch]]))

(def ^:const +public-deploy+
  "Constant to determine is it a public installation on GAE."
  false)
(def ^:const +worker-url-prefix+ "worker.")
(def ^:const +custom-ns+ "feedxcavator.custom-code")

;; available in the context of request handler calls
(def ^:dynamic *worker-instance* "Executing in a background instance." nil)
(def ^:dynamic *servlet-context* "A servlet context instance." nil)
(def ^:dynamic *remote-addr* "Request remote address." nil)
(def ^:dynamic *app-host* "Application server host name (with protocol scheme)." nil)

;; we need some layer of abstraction, because the application may be ported to
;; a plain servlet container (for standalone usage) some time, although this have
;; no sense for Google Reader or Feedly without special measures
;; (forwarding through an external domain)

;; use :gae or :servlet (in case of a plain servlet container implementation, which
;; does not exist yet)
(def ^:const +platform+
  "The current underlying platform, :gae or :servlet"
  :gae)

;; recaptcha private key (needed only for public installations)
(def ^:const +re-private-key+ "")

(def ^:const +feed-url-base+ "/deliver?feed=")
(def ^:const +edit-url-base+ "/edit?feed=")
(def ^:const +delete-url-base+ "/delete?feed=")
(def ^:const +duplicate-url-base+ "/double?feed=")
(def ^:const +manager-url-base+ "/manage")

(def ^:dynamic *feed-settings*)

(defn get-sub-hub-url [] (str *app-host* "/hub"))

(defn peel-list [the-list]
  (let [first-arg (first the-list)]
    (if (list? first-arg) first-arg the-list)))

(defmacro defapi [fn-name doc [& args] & impl-kvs]
  (let [computed-args (peel-list (mexpand args))]
    `(defn ~fn-name ~doc [~@computed-args]
       ~@((apply hash-map impl-kvs) +platform+))))

(defn get-feed-url [uuid]
  (str *app-host* +feed-url-base+ uuid))

;; url fetching ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defapi fetch-url
  "Returns platform-specific response of url-fetching routine, params are also
platform-specific (use feedxcavator.api/+platform+ to detect the current platform).
Useful to retreive HTTP headers from a platform-specific response in custom excavators."
  [url & params]
  :gae [(let [params (if (some #{:deadline} params) params (concat params [:deadline 60]))]
          (apply fetch (cons url params))) ])

(defn fix-relative
  "Transforms relative URLs to absolute (may be needed by a feed reader for headline identification purposes)."
  [url feed-settings]
  (when (not (str/blank? url))
    (let [target-url (:target-url feed-settings)
          target-domain (re-find #"(http.?://)?([^/]+)/?" target-url)
          target-level (get (re-find #"(.*)/[^/]*$" target-url) 1)]
      (cond
        (.startsWith url "//") (str "http:" url)
        (.startsWith url "/") (str (second target-domain) (last target-domain) url)
        (.startsWith url ".") (str target-level (.substring url 1))
        (= (.indexOf url "://") -1) (str target-level
                                         (when (not (.endsWith target-level "/")) "/")
                                         url)
        :default url))))


;; data persistence ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn timestamp []
  (long (/ (System/currentTimeMillis) 1000)))

(defn resp->enlive
  "Transforms a platform-specific response obtained with fetch-url api
function to a format which is edible by enlive selection functions.
May return nil in case if this is not possible."
  ([response] (resp->enlive response *feed-settings*))
  ([response feed-settings]
    (when (= (:response-code response) 200)
            (let [content-type ((:headers response) "Content-Type")
                  charset (if content-type
                            (let [charset= (.indexOf content-type "charset=")]
                              (if (>= charset= 0)
                                (.substring content-type (+ charset= 8))
                                (if (string? feed-settings) feed-settings (:charset feed-settings))))
                            (if (string? feed-settings) feed-settings (:charset feed-settings)))]
              (enlive/html-resource (java.io.InputStreamReader.
                                     (java.io.ByteArrayInputStream. (:content response))
                                     charset))))))

(defn str->enlive [s]
  (enlive/html-resource (java.io.StringReader. s)))

(defn resp->enlive-xml
  "Transforms a platform-specific response obtained with fetch-url api
function to a format which is edible by enlive selection functions.
May return nil in case if this is not possible."
  ([response] (resp->enlive-xml response *feed-settings*))
  ([response feed-settings]
    (when (= (:response-code response) 200)
            (let [content-type ((:headers response) "Content-Type")
                  charset (if content-type
                            (let [charset= (.indexOf content-type "charset=")]
                              (if (>= charset= 0)
                                (.substring content-type (+ charset= 8))
                                (if (string? feed-settings) feed-settings (:charset feed-settings))))
                            (if (string? feed-settings) feed-settings (:charset feed-settings)))]
              (enlive/xml-resource (java.io.InputStreamReader.
                                     (java.io.ByteArrayInputStream. (:content response))
                                     charset))))))

(defn resp->str
  "Transforms a platform-specific response obtained with fetch-url api
function to a format which is edible by enlive selection functions.
May return nil in case if this is not possible."
  ([response] (resp->str response *feed-settings*))
  ([response feed-settings]
    (when (= (:response-code response) 200)
            (let [content-type ((:headers response) "Content-Type")
                  charset (if content-type
                            (let [charset= (.indexOf content-type "charset=")]
                              (if (>= charset= 0)
                                (.substring content-type (+ charset= 8))
                                (if (string? feed-settings) feed-settings (:charset feed-settings))))
                            (if (string? feed-settings) feed-settings (:charset feed-settings)))]
              (slurp (:content response) :encoding charset)))))

(defn url-encode-utf8 [str]
  (URLEncoder/encode str "UTF-8"))

(defapi confirmation-valid?
  "Verify captcha."
  [response challenge]
  :gae [(or (not +public-deploy+)
            (let [params (str "response=" (url-encode-utf8 response)
                              "&challenge=" (url-encode-utf8 challenge)
                              "&remoteip=" (url-encode-utf8 feedxcavator.api/*remote-addr*)
                              "&privatekey=" +re-private-key+)
                  response (-> (fetch "http://www.google.com/recaptcha/api/verify"
                                      :method :post
                                      :payload (.getBytes params "UTF-8"))
                               :content
                               slurp)]
              (>= (.indexOf response "true") 0)))])

(defapi queue-add!
  "Add a task to the queue"
  [url payload]
  :gae [ (queue/add! :url url :payload payload :headers {"Content-Type" "text/plain"}) ])

(defapi named-queue-add!
  "Add a task to the queue"
  [queue url payload]
  :gae [ (queue/add! :url url :payload payload :queue queue :headers {"Content-Type" "text/plain"}) ])

(defn get-app-host []
  (let [env (System/getProperty "com.google.appengine.runtime.environment")]
    (if (= env "Production")
      (str "http://" (System/getProperty "com.google.appengine.application.id") ".appspot.com")
      "http://localhost:8080")))
  
;; memcache ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; memcache is not used currently

(defapi cache-contains?
  "Check for value existence in memory cache."
  [key]
  :gae [ (cache/contains? key) ])

(defapi cache-get
  "Get value from memory cache."
  [key]
  :gae [ (cache/get key) ])

(defapi cache-put!
  "Put value to memory cache. Expires after 10 minutes."
  [key value]
  :gae [ (cache/put! key value :expiration (Expiration/byDeltaSeconds 600)) ])

;; authentication ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defapi user-admin?
  "Checks if logged in user is admin."
  [handler]
  :gae [ (user/user-admin?) ])

;; resources ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defapi get-resource-as-stream
  "Opens resource file as a stream."
  [path]
  :gae [ (ae/open-resource-stream path) ])

(defn compile-custom-code [code]
  (binding [*ns* (find-ns (symbol +custom-ns+))]
    (load-string (str ;"(in-ns '" +custom-ns+ ")"
                       "($cleanup-tasks)"
                       code))))

;; ring responses ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn permission-denied []
  {:status 403
   :headers {"Content-Type" "text/html"}
   :body "<h2>Permission denied</h2>"})

(defn page-not-found []
  {:status 404
   :headers {"Content-Type" "text/html"}
   :body "<h2>Page not found</h2>"})

(defn internal-server-error []
  {:status 505
   :headers {"Content-Type" "text/html"}
   :body "<h2>Internal server error</h2>"})

(defn page-found [content-type body]
  {:status 200
   :headers {"Content-Type" content-type,
             "Cache-Control" "no-cache"}
   :body body})

(defn attachment-page [filename body]
  {:status 200
   :headers {"Content-Disposition" (str "attachment; filename=" filename),
             "Cache-Control" "no-cache"}
   :body body})

(defn html-page [body]
  {:status 200
   :headers {"Content-Type" "text/html"
             "Cache-Control" "no-cache"}
   :body body})

(defn text-page [body]
  {:status 200
   :headers {"Content-Type" "text/plain",
             "Cache-Control" "no-cache"}
   :body body})

(defn redirect-to
  [location]
  {:status 302
   :headers {"Location" location}})

(defn base64enc [str]
  (Base64/encodeBase64URLSafeString (.getBytes str "UTF-8")))

(defn base64dec [str]
  (let [dec (Base64. true)]
    (String. (.decode dec str) "UTF-8")))

(defn base64dec-unsafe [str]
  (let [dec (Base64.)]
    (String. (.decode dec str) "UTF-8")))

(defn base64dec-unsafe-bytes [str]
  (let [dec (Base64.)]
    (.decode dec str)))

(defn proxify [url referer cookie]
  (let [response (fetch-url (base64dec url) :deadline 60 
                            :headers {"Referer" (base64dec referer)
                                      "Cookie" (base64dec cookie)})]
    (page-found (or ((:headers response) "Content-Type")
                    ((:headers response) "content-type"))
                (:content response))))

;; misk ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn apply-selectors
  "Applies CSS selectors from the feed settings to a HTML page content.
Returns list of hash-maps with extracted headline data (hash map keys correspond to the selector keys)."
  ([doc-tree]
   (apply-selectors doc-tree *feed-settings*))
  ([doc-tree feed-settings]
   (apply-selectors doc-tree feed-settings :href :src))
  ([doc-tree feed-settings link-selector image-selector]
   (let [selectors (:enlive-selectors feed-settings)]
     (letfn [(instantiate [sel]
               (postwalk #(if (symbol? %) (ns-resolve 'net.cgrand.enlive-html %) %)
                         (postwalk #(if (list? %) (if (var? (first %)) (apply (first %) (rest %)) %) %)
                                   (postwalk #(if (list? %)
                                                (if (symbol? (first %))
                                                  (list (ns-resolve 'net.cgrand.enlive-html (first %))
                                                        (rest %))
                                                  %)
                                                %) sel))))
             (get-selector [key n] ; get selector fom a set numbered n, get the first selector otherwise
               (let [sel (selectors key)]
                 (when sel
                   (let [sel (if (>= (dec (count sel)) n)
                               (get sel n)
                               (first sel))]
                     (instantiate sel)))))]
       (loop [n 0 selector-set (:headline selectors) headlines (transient [])]
         (let [headline-sel (instantiate (first selector-set))]
           (if headline-sel
             (do
               (doseq [headline-html (enlive/select doc-tree headline-sel)]
                 (letfn [(select-element [category] ; select an element from headline html
                           (first (enlive/select headline-html (get-selector category n))))
                         (get-content [selection] ; get the selected element content
                           (apply str (enlive/emit* (:content selection))))
                         (get-link [category attr] ; extract link from the selected element
                           (if link-selector
                             (fix-relative (attr (:attrs (select-element category)))
                                               feed-settings)
                             (str/trim (apply str (enlive/emit* (:content (select-element category)))))))]
                   (let [headline-data {:title (get-content (select-element :title))
                                        :link (get-link :link link-selector)
                                        :summary (get-content (select-element :summary))
                                        :image (get-link :image image-selector)
                                        :html headline-html}]
                     (when (some #(and (string? (second %)) (not (str/blank? (second %)))) headline-data)
                       (conj! headlines headline-data)))))
               (recur (inc n) (next selector-set) headlines))
             (let [headlines (persistent! headlines)]
               ;; because filter-read-articles function may filter out all headlines, metadata is the only
               ;; way to determine if selectors have selected nothing
               (if (seq headlines)
                 (with-meta headlines {:n-articles (count headlines)})
                 (with-meta [] {:out-of-sync true}))))))))))

(defmacro safely-repeat [statement]
  `(try
     ~statement
     (catch Exception e#
       (try
         ~statement
         (catch Exception e2#
           (println (.getMessage e2#)))))))

(defmacro safely-repeat3 [statement]
  `(try
     ~statement
     (catch Exception e#
       (try
         ~statement
         (catch Exception e2#
           (try
             ~statement
             (catch Exception e3#
               (println (.getMessage e2#)))))))))

(defapi in-debug-env?
  "Was the application launched in debug environment?"
  []
  :gae [ (not= appengine-magic.core/appengine-environment-type :production) ])

(defmacro with-meta-from [obj recipient]
  `(with-meta ~recipient (meta ~obj)))

(defmacro alter-meta [obj & kv]
  `(let [obj# ~obj]
     (with-meta obj# (apply assoc (cons (meta obj#) ~kv)))))
                
(defn get-uuid
  "Get globally unique identifier."
  []
  (.replaceAll (str (java.util.UUID/randomUUID)) "-" ""))

(defn md5 [s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        size (* 2 (.getDigestLength algorithm))
        raw (.digest algorithm (.getBytes s))
        sig (.toString (BigInteger. 1 raw) 16)
        padding (apply str (repeat (- size (count sig)) "0"))]
    (str padding sig)))

(defn render
  "Transforms an enlive template to string."
  [nodeset]
  (apply str (enlive/emit* nodeset)))

(defn html-unescape [s]
  (str/replace (str/replace (str/replace (str/replace s "&lt;" "<") "&gt;" ">") "&amp;" "&") "&quot;" "&"))

(defn sanitize
  "Escape a string for insertion into HTML code."
  [content]
  (str/escape (str content) {\< "&lt;" \> "&gt;" \" "&quot;"}))

(defn untag [s]
  (str/replace s #"</?[a-z,A-Z]+>" ""))