(ns feedxcavator.db
  (:require [appengine-magic.services.datastore :as ds]
            [clojure.java.io :as io]
            [feedxcavator.api :as api])
  (:use [feedxcavator.api :only [defapi]]
        clojure.tools.macro)
  (:import java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream))

(def ^:const +db-record-limit+ 1048576)

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
                             custom-params
                             ;^:clj test-field
                             ))

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
(defsymbolmacro settings-fields (^:key id sender-mail recipient-mail report))
(defsymbolmacro user-sample-fields (^:key id headlines date last-fetch-count))
(defsymbolmacro word-filter-fields (^:key expr type))

(defsymbolmacro sid-fields (^:key id))

(case api/+platform+
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
         (defentity Settings settings-fields)
         (defentity UserSample user-sample-fields)
         (defentity Sid sid-fields)
         (defentity WordFilter word-filter-fields)
         ))

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

(defapi query-feed
        "Reads feed settings from database."
        [feed-id]
  :gae [ (when-let [feed (ds/retrieve Feed feed-id)]
           (if (:custom-params feed)
             (assoc feed :custom-params (.getValue (:custom-params feed)))
             feed))
        ])

(defapi get-all-feeds
        "Gets settings of all stored feeds."
        []
  :gae [ (let [feeds (ds/query :kind Feed)]
           (for [feed feeds]
             (if (:custom-params feed)
               (assoc feed :custom-params (.getValue (:custom-params feed)))
               feed)))
        ])

;; (defapi get-realtime-feeds
;;         "Gets settings of all realtime feeds."
;;         []
;;         :gae [ (ds/query :kind Feed :filter (= :realtime true)) ])

(defapi store-feed!
        "Stores feed settings in database."
        [feed-settings]
  :gae [
        (let [feed-settings (if (string? (:custom-params feed-settings))
                              (assoc feed-settings :custom-params (ds/as-text (:custom-params feed-settings)))
                              feed-settings)]
          (println feed-settings)
          (ds/save! (map->Feed feed-settings))) ])

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
                     feed-url (str api/*app-host* api/+feed-url-base+ (:uuid feed-settings))
                     [content compressed?] (shrink-for-ds content)]
                 (ds/save! (StoredRSS. feed-id content compressed?))
                 (when (:realtime feed-settings)
                   (api/fetch-url (api/get-sub-hub-url) :method :post
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
                     (ds/save! (StoredImage. name url (api/timestamp) (ds/as-blob data)))
                     name))) ])

(defapi delete-images!
        ""
        [before-date]
        :gae [ (ds/delete! (ds/query :kind StoredImage :filter (< :timestamp before-date))) ])

(defapi query-history
        ""
        [url]
        :gae [ (let [history (ds/retrieve AccessHistory url)]
                 (or history {:entries #{}})) ])

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
                 (or history {:entries #{}})) ])

(defapi store-fetcher-history!
        ""
        [uuid history]
        :gae [ (ds/save! (FetcherHistory. uuid (set history) false)) ])

(defapi delete-fetcher-history!
        ""
        []
        :gae [ (ds/delete! (ds/query :kind FetcherHistory)) ])

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
        :gae [ (ds/save! (Subscription. uuid name topic callback secret (api/timestamp))) ])

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
        :gae [ (ds/save! (HttpCookie. domain content (api/timestamp))) ])

(defapi delete-cookie!
        ""
        [domain]
        :gae [ (ds/delete! (ds/retrieve HttpCookie domain)) ])

(defapi query-settings
        ""
        []
        :gae [ (or (ds/retrieve Settings "global") {})])

(defapi store-settings!
        ""
        [settings]
        :gae [ (ds/save! (map->Settings (assoc settings :id "global"))) ])

(defapi query-user-sample
        ""
        [id]
        :gae [ (ds/retrieve UserSample id) ])

(defapi store-user-sample!
        ""
        [id headlines date last-fetch-count]
        :gae [ (ds/save! (UserSample. id headlines date last-fetch-count)) ])

(defapi get-sid
        "Gets settings of all stored feeds."
        []
        :gae [ (first (ds/query :kind Sid)) ])

(defapi store-sid!
        ""
        [id]
        :gae [ (ds/save! (Sid. id)) ])


(defapi get-all-words
  ""
  []
  :gae [ (ds/query :kind WordFilter) ])

(defapi store-word!
  "Stores feed settings in database."
  [word type]
  :gae [ (ds/save! (WordFilter. word type)) ])

(defapi delete-word!
  "Deletes the given feed."
  [word]
  :gae [ (ds/delete! (ds/retrieve WordFilter word)) ])


(defapi backup-database
        ""
        []
        :gae [(pr-str
                {
                 :feeds (map #(into {} %) (ds/query :kind Feed))
                 :subscriptions (map #(into {} %) (ds/query :kind Subscription))
                 :custom-code (query-custom-code)
                 :settings (query-settings)
                 :word-filter (map #(into {} %) (get-all-words))
                 })
              ])

(defapi restore-database
        ""
        [edn]
        :gae [(let [data (read-string edn)]
                (doseq [f (:feeds data)]
                  (store-feed! (map->Feed f)))
                (doseq [s (:subscriptions data)]
                  (store-subscription! (:uuid s) (:name s) (:topic s) (:callback s) (:secret s)))
                (store-custom-code! (:custom-code data))
                (store-settings! (:settings data))
                (doseq [w (:word-filter data)]
                  (store-word! (:expr w) (:type w))))
              ])

