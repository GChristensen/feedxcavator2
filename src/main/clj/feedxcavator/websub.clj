(ns feedxcavator.websub
  (:require [feedxcavator.core :as core]
            [feedxcavator.db :as db]
            [clojure.string :as str]
            [feedxcavator.log :as log]))

(defn feed-from-url [url]
  (let [suffix (last (str/split url #"/feed/"))]
    (if (str/starts-with? suffix "uuid:")
      (first (db/fetch :feed (last (str/split suffix #":"))))
      (first (db/query :feed (= :suffix suffix))))))

(defn subscribe [params]
  (let [topic (params "hub.topic")
        feed (feed-from-url topic)]
    (db/store! :subscription {:uuid (:uuid feed)
                              :name (:title feed)
                              :topic topic
                              :callback (params "hub.callback")
                              :secret (params "hub.secret")
                              :timestamp (core/timestamp)})
    (core/fetch-url (str (params "hub.callback")
                         (str "&hub.lease_seconds=" (params "hub.lease_seconds"))
                         (str "&hub.topic=" (params "hub.topic"))
                         (str "&hub.mode=" (params "hub.mode"))
                         (str "&hub.challenge=" (core/generate-uuid)))
                    :async? false)
    {:status 202}))

(defn unsubscribe [params]
  (let [topic (params "hub.topic")
        feed (feed-from-url topic)]
    (db/delete! :subscription (:uuid feed))
    (core/fetch-url (str (params "hub.callback")
                         (str "&hub.lease_seconds=" (params "hub.lease_seconds"))
                         (str "&hub.topic=" (params "hub.topic"))
                         (str "&hub.mode=" (params "hub.mode"))
                         (str "&hub.challenge=" (core/generate-uuid)))
                    :async? true)
    {:status 202}))

(defn publish-content [uuid topic content]
  (when-let [subscr (db/fetch :subscription uuid)]
    (let [sig (when (:secret subscr)
                (core/sha1-sign (:output content) (:secret subscr)))
          headers {"Content-Type" (str (:content-type content) "; charset=utf-8")
                   "Link" (str "<" topic ">; rel=\"self\", <"
                               (core/get-websub-url) ">; rel=\"hub\"")}
          headers (if sig
                    (assoc headers "X-Hub-Signature" (str "sha1=" sig))
                    headers)
          callback (:callback subscr)
          callback (if (str/includes? callback "feedly.com")
                         (str callback "&hub.mode=publish")
                         callback)
          response (core/fetch-url callback
                                   :method :post
                                   :headers headers
                                   :payload (.getBytes (:output content) "utf-8"))]
      (when (nil? response)
        (let [error (format "HTTP error during WebSub publishing: %d\nresponse content: %s\nHTTP response: %s"
                            (core/get-last-http-error)
                            (slurp (:content (core/get-last-http-response)))
                            (with-out-str (clojure.pprint/pprint (core/get-last-http-response))))]
          (log/write :error error)))
      {:status 204})))

(defn publish [params]
  (let [topic (params "hub.url")
        feed (feed-from-url topic)
        uuid (:uuid feed)
        content (:output (db/fetch-feed-output uuid))]
    (publish-content uuid topic content)))

(defn hub-action [request]
  (if (= :post (:request-method request))
    (case ((:params request) "hub.mode")
      "subscribe" (subscribe (:params request))
      "unsubscribe" (unsubscribe (:params request))
      "publish" (publish (:params request)))))