(ns feedxcavator.core
  (:import [java.net URLEncoder]
           org.apache.commons.codec.binary.Base64
           java.security.MessageDigest
           java.math.BigInteger
           javax.crypto.spec.SecretKeySpec
           javax.crypto.Mac
           java.util.Formatter
           com.google.appengine.api.utils.SystemProperty
           java.security.SecureRandom)
  (:require [appengine-magic.core :as ae]
            [appengine-magic.services.user :as user]
            [appengine-magic.services.task-queues :as queue]
            [appengine-magic.services.url-fetch :as ae-fetch]
            [net.cgrand.enlive-html :as enlive]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clj-time.core :as tm])
  (:use [clojure.java.io :only [input-stream]]
        clojure.tools.macro
        clojure.walk))

(def ^:const app-version "2.1.0")

(def ^:const deployment-type :private) ;; :private, :demo
(def ^:const blob-implementation :cloudstorage) ;; :cloudstorage, :datastore

(def ^:const worker-url-prefix "worker")

(def ^:const user-code-ns "feedxcavator.code-user")

;; available in the context of request handler calls
(def ^:dynamic *worker-instance* "Executing in a background instance." nil)
(def ^:dynamic *servlet-context* "A servlet context instance." nil)
(def ^:dynamic *remote-addr* "Request remote address." nil)
(def ^:dynamic *app-host* "Application server host name (with protocol scheme)." nil)

(def ^:dynamic *current-feed*)
(def ^:dynamic *current-logging-source*)

;; ring responses ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn permission-denied []
  {:status  403
   :headers {"Content-Type" "text/html"}
   :body    "<h2>Permission denied</h2>"})

(defn page-not-found []
  {:status  404
   :headers {"Content-Type" "text/html"}
   :body    "<h2>Page not found</h2>"})

(defn internal-server-error []
  {:status  500
   :headers {"Content-Type" "text/html"}
   :body    "<h2>Internal server error</h2>"})

(defn internal-server-error-message [text]
  {:status  500
   :headers {"Content-Type" "text/html"}
   :body    text})

(defn no-content []
  {:status  204
   :headers {"Cache-Control" "no-cache"}})

(defn web-page [content-type body]
  {:status  200
   :headers {"Content-Type"  content-type,
             "Cache-Control" "no-cache"}
   :body    body})

(defn attachment-page [filename body]
  {:status  200
   :headers {"Content-Disposition" (str "attachment; filename=" filename),
             "Cache-Control"       "no-cache"}
   :body    body})

(defn rss-page [body]
  {:status  200
   :headers {"Content-Type"  "application/rss+xml"
             "Cache-Control" "no-cache"}
   :body    body})

(defn json-page [body]
  {:status  200
   :headers {"Content-Type"  "application/json"
             "Cache-Control" "no-cache"}
   :body    body})

(defn html-page [body]
  {:status  200
   :headers {"Content-Type"  "text/html"
             "Cache-Control" "no-cache"}
   :body    body})

(defn text-page [body]
  {:status  200
   :headers {"Content-Type"  "text/plain",
             "Cache-Control" "no-cache"}
   :body    body})

(defn redirect-to
  [location]
  {:status  302
   :headers {"Location" location}})

;; utility ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn timestamp []
  (System/currentTimeMillis))

(defn distinct-by [f coll]
  (loop [items coll seen #{} result []]
    (let [item (first items)]
      (if item
        (let [field (f item)]
          (if (seen field)
            (recur (next items) seen result)
            (recur (next items) (conj seen field) (conj result item))))
        result))))

(def regex-char-esc-smap
  (let [esc-chars "()&^%$#!?*."]
    (zipmap esc-chars
            (map #(str "\\" %) esc-chars))))

(defn regex-escape [string]
  (->> string
       (replace regex-char-esc-smap)
       (reduce str)))

(defn generate-uuid
  "Get globally unique identifier."
  []
  (.replaceAll (str (java.util.UUID/randomUUID)) "-" ""))

(defn to-hex-string [bytes]
  (let [formatter (Formatter.)]
    (doseq [b bytes]
      (let [arg (make-array Byte 1)]
        (aset arg 0 b)
        (.format formatter "%02x" arg)))
    (.toString formatter)))

(defn md5 [s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        size (* 2 (.getDigestLength algorithm))
        raw (.digest algorithm (.getBytes s))
        sig (.toString (BigInteger. 1 raw) 16)
        padding (apply str (repeat (- size (count sig)) "0"))]
    (str padding sig)))

(defn sha1-sign [data key]
  (let [key (SecretKeySpec. (.getBytes key "utf-8") "HmacSHA1")
        mac (Mac/getInstance "HmacSHA1")]
    (.init mac key)
    (to-hex-string (.doFinal mac (.getBytes data "utf-8")))))

(defn generate-random [byte-length]
  (let [bytes (make-array Byte/TYPE byte-length)]
    (.nextBytes (SecureRandom.) bytes)
    (to-hex-string bytes)))

(defn url-encode-utf8 [str]
  (URLEncoder/encode str "UTF-8"))

(defn url-safe-base64enc [str]
  (Base64/encodeBase64URLSafeString (.getBytes str "UTF-8")))

(defn url-safe-base64dec [str]
  (let [dec (Base64. true)]
    (String. (.decode dec str) "UTF-8")))

(defn base64enc [str]
  (let [dec (Base64.)]
    (.encode (.getBytes str "UTF-8"))))

(defn base64dec [str]
  (let [dec (Base64.)]
    (String. (.decode dec str) "UTF-8")))

(defn base64dec->bytes [str]
  (let [dec (Base64.)]
    (.decode dec str)))

;; html ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn html-render [nodeset]
  (apply str (enlive/emit* nodeset)))

(defn html-unescape [s]
  (str/replace (str/replace (str/replace (str/replace s "&lt;" "<") "&gt;" ">") "&amp;" "&") "&quot;" "&"))

(defn html-sanitize [content]
  (str/escape (str content) {\< "&lt;" \> "&gt;" \" "&quot;" \' "&#39;" \& "&amp;"}))

(defn html-untag [s]
  (str/replace s #"</?[a-z,A-Z]+>" ""))

(defn html-format [html]
  html)

(defn xml-format [xml]
  (when xml
    (let [correct-xml (str/replace xml #"xml:base=\"[^\"]*\"" "")
          formatted-xml (xml/indent-str (xml/parse-str correct-xml))]
      (str/replace formatted-xml "<?xml version=\"1.0\" encoding=\"UTF-8\"?><"
                   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<"))))

;; misk ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro safely-repeat [statement]
  `(try
     ~statement
     (catch Exception e#
       (try
         ~statement
         (catch Exception e2#)))))

(defmacro safely-repeat3 [statement]
  `(try
     ~statement
     (catch Exception e#
       (try
         ~statement
         (catch Exception e2#
           (try
             ~statement
             (catch Exception e3#)))))))

(defn kebab-to-pascal [name]
  (let [words (str/split name #"-")]
    (str/join "" (map #(str/capitalize %) words))))

;; application ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn production? []
  (= (ae/appengine-environment-type) :production))

(def application-id (.get SystemProperty/applicationId))

(defn get-app-host []
  (if (production?)
    (str "https://" application-id ".appspot.com")
    "http://localhost:8080"))

(defn redirect-url [url]
  (str *app-host* "/redirect/" (generate-random 10) "/" (url-encode-utf8 url)))

(defn get-websub-url [] (str *app-host* "/websub"))

(defn get-feed-url [feed]
  (if (:suffix feed)
    (str *app-host* "/feed/" (:suffix feed))
    (str *app-host* "/feed/uuid:" (:uuid feed))))

(defn fix-relative-url [url base]
  (when (not (str/blank? url))
    (let [target-url base
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

(defn find-header [response header]
  (second (first (filter #(= (str/lower-case header)
                             (str/lower-case (first %)))
                         (:headers response)))))

(defn response-charset [response]
  (let [content-type (find-header "Content-Type" response)]
    (when content-type
      (let [charset= (.indexOf (str/lower-case content-type) "charset=")]
        (when (>= charset= 0)
          (.substring content-type (+ charset= 8)))))))

(defn read-response [response default-charset]
  (let [charset (response-charset response)
        charset (if charset
                  charset
                  (if default-charset
                    default-charset
                    "utf-8"))]
    (java.io.InputStreamReader.
      (java.io.ByteArrayInputStream. (:content response))
      charset)))

(defn str->enlive [s]
  (enlive/html-resource (java.io.StringReader. s)))

(defn resp->enlive
  ([response] (resp->enlive response (:charset *current-feed*)))
  ([response default-charset]
   (enlive/html-resource (read-response response default-charset))))

(defn resp->enlive-xml
  ([response] (resp->enlive-xml response (:charset *current-feed*)))
  ([response default-charset]
   (enlive/xml-resource (read-response response default-charset))))

(defn resp->str
  ([response] (resp->str response (:charset *current-feed*)))
  ([response default-charset]
   (let [charset (response-charset response)
         charset (if charset
                   charset
                   (if default-charset
                     default-charset
                     "utf-8"))]
     (slurp (:content response) :encoding charset))))

(defmacro authorized [request & body]
 `(let [auth-token# (:token (feedxcavator.db/fetch :auth-token "main"))]
    (if (and auth-token# (= auth-token# (find-header ~request "x-feedxcavator-auth")))
      (do ~@body)
      (permission-denied))))


(defn queue-add! [url payload]
  (queue/add! :url url :task-name (str (:task-name payload) "-" (generate-random 5))
              :payload (pr-str payload) :headers {"Content-Type" "text/plain"}))

;; url-fetch ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *last-http-response* (atom nil))
(def ^:dynamic *last-http-error-code* (atom nil))
(def ^:dynamic *last-http-network-error* (atom nil))

(defn get-last-http-response []
  @*last-http-response*)

(defn get-last-http-error []
  @*last-http-error-code*)

(defn get-last-network-error []
  @*last-http-network-error*)

(defmacro safely-repeat-fetch-url [statement]
  `(try
     ~statement
     (catch Exception e#
       (try
         ~statement
         (catch Exception e2#
           (reset! *last-http-network-error* e2#))))))

(defn fetch-url [url & params]
  (reset! *last-http-response* nil)
  (reset! *last-http-error-code* nil)
  (reset! *last-http-network-error* nil)

  (let [params-map (apply hash-map params)
        response-type (params-map :as)
        retry (if (false? (params-map :retry)) false true)
        charset (params-map :charset)
        charset (if charset
                  charset
                  (if (:charset *current-feed*)
                    (:charset *current-feed*)
                    "utf-8"))
        params (if (some #{:deadline} params)
                   params
                   (concat params [:deadline (if *worker-instance* 600 60)]))
        response (if retry
                   (safely-repeat-fetch-url (apply ae-fetch/fetch (cons url params)))
                   (apply ae-fetch/fetch (cons url params)))]

    (if (and response (:response-code response) (< (:response-code response) 300))
      (try
        (cond (= response-type :html) (resp->enlive response charset)
              (= response-type :xml) (resp->enlive-xml response charset)
              (= response-type :json) (json/read-str (resp->str response charset))
              (= response-type :string) (resp->str response charset)
              :else response)
        (catch Error e
          (.printStackTrace e)))
      (do
        (reset! *last-http-response* response)
        (reset! *last-http-error-code* (:response-code response))
        nil))))
