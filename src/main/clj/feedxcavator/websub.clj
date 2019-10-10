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
          response (core/fetch-url (:callback subscr)
                                   :method :post
                                   :headers headers
                                   :payload (.getBytes (:output content) "utf-8"))]
      ;(println (str "id: " uuid "\nlen: " (.length (:output content)) "\nresp: "
      ;              (slurp (:content response) :encoding "utf-8")))
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