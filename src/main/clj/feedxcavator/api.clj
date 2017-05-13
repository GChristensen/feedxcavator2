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
           [appengine-magic.services.datastore :as ds]
           [appengine-magic.services.user :as user]
           [appengine-magic.services.task-queues :as queue]
           [appengine-magic.services.memcache :as cache]
           [net.cgrand.enlive-html :as enlive]
           [clojure.java.io :as io]
           [clojure.string :as str]
           [clj-time.core :as tm])
  (:use clojure.tools.macro
        [appengine-magic.services.url-fetch :only [fetch]]))

(def ^:const +public-deploy+
  "Constant to determine is it a public installation on GAE."
  false)

;; available in the context of request handler calls
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

(def ^:const +db-record-limit+ 1048576)

;; recaptcha private key (needed only for public installations)
(def ^:const +re-private-key+ "")

(def ^:const +feed-url-base+ "/deliver?feed=")
(def ^:const +edit-url-base+ "/edit?feed=")
(def ^:const +delete-url-base+ "/delete?feed=")
(def ^:const +duplicate-url-base+ "/double?feed=")
(def ^:const +manager-url-base+ "/manage")

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

(defn gunzip
  "Writes the contents of input to output, decompressed.
  input: something which can be opened by io/input-stream.
      The bytes supplied by the resulting stream must be gzip compressed.
  output: something which can be copied to by io/copy."
  [input output & opts]
  (with-open [input (-> input io/input-stream GZIPInputStream.)]
    (apply io/copy input output opts)))

(defn gzip
  "Writes the contents of input to output, compressed.
  input: something which can be copied from by io/copy.
  output: something which can be opend by io/output-stream.
      The bytes written to the resulting stream will be gzip compressed."
  [input output & opts]
  (with-open [output (-> output io/output-stream GZIPOutputStream.)]
    (apply io/copy input output opts)))

(defn shrink-for-ds [content]
  (let [content-bytes (.getBytes content "utf-8")
;;_ (println (str "Content size: " (alength content-bytes)))
        compress? (>= (alength content-bytes) +db-record-limit+)
        content (if compress? 
                  (let [byte-stream (java.io.ByteArrayOutputStream.)]
                    (gzip content-bytes byte-stream)
                    (.toByteArray byte-stream))
                  content)
        data-fn (if compress? ds/as-blob ds/as-text)]
    [(data-fn content) compress?]))

(defn unshrink-from-ds [content compressed?]
  (if compressed?
    (let [byte-stream (java.io.ByteArrayOutputStream.)]
      (gunzip (.getBytes content) byte-stream)
      (String. (.toByteArray byte-stream) "utf-8"))
    (.getValue content)))


;; url fetching ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defapi fetch-url
  "Returns platform-specific response of url-fetching routine, params are also
platform-specific (use feedxcavator.api/+platform+ to detect the current platform).
Useful to retreive HTTP headers from a platform-specific response in custom excavators."
  [url & params]
  :gae [ (apply fetch (cons url params)) ])

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

(defmacro apply-cons [cons fields]
  (let [computed-fields (mexpand fields)]
    `(~cons ~@computed-fields)))
  
(defmacro defentity [name field-list]
  `(ds/defentity ~name [~@(mexpand `~field-list)]))

(defsymbolmacro feed-fields (^:key uuid
                             feed-title
                             target-url
                             charset
                             ^:clj selectors
                             ^:clj enlive-selectors
                             remember-recent
                             recent-article
                             realtime
                             custom-excavator
                             custom-params))

(defsymbolmacro selector-fields (headline title link summary image))

(defsymbolmacro rss-fields (^:key uuid content compressed))
(defsymbolmacro image-fields (^:key name url timestamp data))
(defsymbolmacro history-fields (^:key url ^:clj entries))
(defsymbolmacro fetcher-history-fields (^:key url ^:clj entries compressed))
(defsymbolmacro custom-code-fields (^:key state code))
(defsymbolmacro external-data-fields (^:key feed-id data compressed))
(defsymbolmacro timestamp-fields (^:key id stamp))
(defsymbolmacro subscription-fields (^:key uuid name topic callback secret timestamp))
(defsymbolmacro cookie-fields (^:key domain content timestamp))

(case +platform+
  :gae (do 
         (defentity Feed feed-fields)
         (defentity StoredRSS rss-fields)
         (defentity StoredImage image-fields)
         (defentity AccessHistory history-fields)
         (defentity FetcherHistory fetcher-history-fields)
         (defentity CustomCode custom-code-fields)
         (defentity ExternalData external-data-fields)
         (defentity Timestamp timestamp-fields)
         (defentity Subscription subscription-fields)
         (defentity HttpCookie cookie-fields)
         ))

(defapi cons-feed
  "Constructs a feed settings entity struct-map."
  [feed-fields]
  :gae [ (apply-cons Feed. feed-fields) ])

(defmacro cons-feed-from-map
  "Constructs a feed settings entity struct-map from a hash-map with the same structure."
  [feed-settings]
  (let [gsettings-map (gensym)]
    `(let [~gsettings-map ~feed-settings]
       (cons-feed ~@(for [param `~(mexpand feed-fields)]
                      (list (keyword param) gsettings-map))))))

(defapi query-feed
  "Reads feed settings from database."
  [feed-id]
  :gae [ (ds/retrieve Feed feed-id) ])

(defapi get-all-feeds
  "Gets settings of all stored feeds."
  []
  :gae [ (ds/query :kind Feed) ])

(defapi get-realtime-feeds
  "Gets settings of all realtime feeds."
  []
  :gae [ (ds/query :kind Feed :filter (= :realtime true)) ])

(defapi store-feed!
  "Stores feed settings in database."
  [feed-settings]
  :gae [ (ds/save! feed-settings) ])

(defapi delete-feed!
  "Deletes the given feed."
  [feed-id]
  :gae [ (ds/delete! (ds/retrieve Feed feed-id)) ])

(defapi query-timestamp
  ""
  [id]
  :gae [ (ds/retrieve Timestamp id) ])

(defapi store-timestamp!
  ""
  [id timestamp]
  :gae [ (ds/save! (Timestamp. id timestamp)) ])

(defapi delete-timestamp!
  ""
  [id]
  :gae [ (ds/delete! (ds/retrieve Timestamp id)) ])

(defapi query-stored-rss
  ""
  [feed-id]
  :gae [ 
        (if feed-id 
          (let [rss (ds/retrieve StoredRSS feed-id)]
            (if rss
              (assoc rss :content (unshrink-from-ds (:content rss) (:compressed rss)))
              {:content ""}))
          {:content ""})])

(defapi store-rss!
  ""
  [feed-id content]
  :gae [ (let [feed-settings (query-feed feed-id)
               feed-url (str *app-host* +feed-url-base+ (:uuid feed-settings))
               [content compressed?] (shrink-for-ds content)]
           (ds/save! (StoredRSS. feed-id content compressed?))
           (when (:realtime feed-settings)
             (fetch-url (get-sub-hub-url) :method :post :deadline 60
                        :headers {"Content-Type" "application/x-www-form-urlencoded"}
                        :payload (.getBytes (str "hub.mode=publish&hub.url="
                                                 (java.net.URLEncoder/encode feed-url))
                                            "UTF-8"))))
           ])

(defapi query-image
  ""
  [name]
  :gae [(ds/retrieve StoredImage name)])

(defapi is-image-there?
  ""
  [url]
  :gae [(let [image (first (ds/query :kind StoredImage :filter (= :url url)))]
           (when image
             (:name image)))])

(defapi store-image!
  ""
  [name url data]
  :gae [ (let [image (first (ds/query :kind StoredImage :filter (= :url url)))]
           (if image
             (:name image)
             (do
               (ds/save! (StoredImage. name url (timestamp) (ds/as-blob data)))
               name))) ])

(defapi delete-images!
  ""
  [before-date]
  :gae [ (ds/delete! (ds/query :kind StoredImage :filter (< :timestamp before-date))) ])

(defapi query-history
  ""
  [url]
  :gae [ (let [history (ds/retrieve AccessHistory url)]
           (if history history {:entries #{}})) ])

(defapi store-history!
  ""
  [url history]
  :gae [ (ds/save! (AccessHistory. url (set history))) ])

(defapi delete-realtime-feed-history!
  ""
  [feed-id]
  :gae [ (ds/delete! (ds/retrieve AccessHistory feed-id)) ])

(defn filter-history [url headlines]
  (let [history (:entries (query-history url))
        result (filter #(not (history (:link %))) headlines)]
    (store-history! url (map #(:link %) headlines))
    result))

(defapi query-fetcher-history
  ""
  [uuid]
  :gae [ (let [history (ds/retrieve FetcherHistory uuid)]
           (if history history {:entries #{}})) ])

(defapi store-fetcher-history!
  ""
  [uuid history]
  :gae [ (ds/save! (FetcherHistory. uuid (set history) false)) ])

(defapi delete-fetcher-history!
  ""
  []
  :gae [ (ds/delete! (ds/query :kind FetcherHistory)) ])

(def ^:const +custom-ns+ "feedxcavator.custom-code")

(defn set-custom-ns [code]
  (str "(in-ns '" +custom-ns+ ")"  code))
    
(defapi query-custom-code
  ""
  []
  :gae [(.getValue (:code (ds/retrieve CustomCode "current")))])

(defapi store-custom-code!
  ""
  [code]
  :gae [ (ds/save! (CustomCode. "current" (ds/as-text code))) ])

(defapi query-external-data
  ""
  [feed-id]
  :gae [
        (let [data (ds/retrieve ExternalData feed-id)]
            (if data
              (unshrink-from-ds (:data data) (:compressed data))))
              ])

(defapi store-external-data!
  ""
  [feed-id data]
  :gae [ 
        (let [[content compressed?] (shrink-for-ds data)]
          (ds/save! (ExternalData. feed-id content compressed?)))
          ])

(defapi query-subscription
  ""
  [uuid]
  :gae [ (ds/retrieve Subscription uuid) ])

(defapi store-subscription!
  ""
  [uuid name topic callback secret]
  :gae [ (ds/save! (Subscription. uuid name topic callback secret (timestamp))) ])

(defapi delete-subscription!
  ""
  [uuid]
  :gae [ (ds/delete! (ds/retrieve Subscription uuid)) ])


(defapi query-cookie
  ""
  [domain]
  :gae [ (ds/retrieve HttpCookie domain) ])

(defapi store-cookie!
  ""
  [domain content]
  :gae [ (ds/save! (HttpCookie. domain content (timestamp))) ])

(defapi delete-cookie!
  ""
  [domain]
  :gae [ (ds/delete! (ds/retrieve HttpCookie domain)) ])

(defapi backup-database
  ""
  []
  :gae [(pr-str 
         {
          :feeds (map #(into {} %) (ds/query :kind Feed))
          :subscriptions (map #(into {} %) (ds/query :kind Subscription))
          :custom-code (query-custom-code)
          })
         ])

(defapi restore-database
  ""
  [edn]
  :gae [(let [data (read-string edn)]
          (doseq [f (:feeds data)]
            (store-feed! (cons-feed-from-map f)))
          (doseq [s (:subscriptions data)]
            (store-subscription! (:uuid s) (:name s) (:topic s) (:callback s) (:secret s)))
          (store-custom-code! (:custom-code data)))
         ])

(defapi make-enlive-resource
  "Transforms a platform-specific response obtained with fetch-url api
function to a format which is edible by enlive selection functions.
May return nil in case if this is not possible."
  [response feed-settings]
  :gae [(when (= (:response-code response) 200)
          (let [content-type ((:headers response) "Content-Type")
                charset (if content-type
                          (let [charset= (.indexOf content-type "charset=")]
                            (if (>= charset= 0)
                              (.substring content-type (+ charset= 8))
                              (:charset feed-settings)))
                          (:charset feed-settings))]
            (enlive/html-resource (java.io.InputStreamReader.
                                   (java.io.ByteArrayInputStream. (:content response))
                                   charset))))])

(defapi make-enlive-resource-xml
  "Transforms a platform-specific response obtained with fetch-url api
function to a format which is edible by enlive selection functions.
May return nil in case if this is not possible."
  [response feed-settings]
  :gae [(when (= (:response-code response) 200)
          (let [content-type ((:headers response) "Content-Type")
                charset (if content-type
                          (let [charset= (.indexOf content-type "charset=")]
                            (if (>= charset= 0)
                              (.substring content-type (+ charset= 8))
                              (:charset feed-settings)))
                          (:charset feed-settings))]
            (enlive/xml-resource (java.io.InputStreamReader.
                                   (java.io.ByteArrayInputStream. (:content response))
                                   charset))))])

(defapi make-string-resource
  "Transforms a platform-specific response obtained with fetch-url api
function to a format which is edible by enlive selection functions.
May return nil in case if this is not possible."
  [response feed-settings]
  :gae [(when (= (:response-code response) 200)
          (let [content-type ((:headers response) "Content-Type")
                charset (if content-type
                          (let [charset= (.indexOf content-type "charset=")]
                            (if (>= charset= 0)
                              (.substring content-type (+ charset= 8))
                              (:charset feed-settings)))
                          (:charset feed-settings))]
            (slurp (:content response) :encoding charset)))])

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