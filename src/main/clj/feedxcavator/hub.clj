(ns feedxcavator.hub
  (:require [feedxcavator.api :as api]
            [clojure.string :as str]
            [clj-time.core :as tm]
            [clj-time.format :as fmt])
  (:import [feedxcavator HmacSha1Signature]))

(defn subscribe [params]
  (if (> (.indexOf (params "hub.callback") "superfeedr.com") 0)
    {:status 404}
    (let [topic (params "hub.topic")
          uuid (.substring topic (inc (.lastIndexOf topic "=")))
          feed-settings (api/query-feed uuid)]
      (api/store-subscription! uuid (:feed-title feed-settings)
                               topic (params "hub.callback") 
                               (params "hub.secret"))
      (api/fetch-url (str (params "hub.callback") 
                          (str "&hub.lease_seconds=" (params "hub.lease_seconds"))
                          (str "&hub.topic=" (params "hub.topic"))
                          (str "&hub.mode=" (params "hub.mode"))
                          (str "&hub.challenge=" (api/get-uuid)))
                     :deadline 60
                     :async? true)
      {:status 202})))

(defn unsubscribe [params]
  (let [topic (params "hub.topic")
        uuid (.substring topic (inc (.lastIndexOf topic "=")))]
    (api/delete-subscription! uuid)
    (api/fetch-url (str (params "hub.callback") 
                        (str "&hub.lease_seconds=" (params "hub.lease_seconds"))
                        (str "&hub.topic=" (params "hub.topic"))
                        (str "&hub.mode=" (params "hub.mode"))
                        (str "&hub.challenge=" (api/get-uuid)))
                   :deadline 60
                   :async? true)
    {:status 202}))

(defn publish [params]
  (let [topic (params "hub.url")
        uuid (.substring topic (inc (.lastIndexOf topic "=")))]
    (when-let [subscr (api/query-subscription uuid)]
      (let [payload (:content (api/query-stored-rss uuid))
            sig (when (:secret subscr) 
                  (HmacSha1Signature/calculateRFC2104HMAC payload (:secret subscr)))
            headers {"Content-Type" "application/rss+xml; charset=utf-8"
                     "Link" (str "<" topic ">; rel=\"self\", <" 
                                 (api/get-sub-hub-url) ">; rel=\"hub\"")}
            headers (if sig
                      (assoc headers "X-Hub-Signature" (str "sha1=" sig))
                      headers)
            
            response (api/fetch-url (:callback subscr)
                                    :deadline 60
                                    :method :post
                                    :headers headers
                                    :payload (.getBytes payload "utf-8"))]
(println (str "id: " uuid "\nlen: " (.length payload) "\nresp: " 
              (slurp (:content response) :encoding "utf-8")))
;(println (pr-str response))
        {:status 204}))))

(defn post-action [request]
  (if (= :post (:request-method request))
    (case ((:params request) "hub.mode")
      "subscribe" (subscribe (:params request))
      "unsubscribe" (unsubscribe (:params request))
      "publish" (publish (:params request)))))
    
(defn get-action [request]

)

(defn publish-notify [feed]
  (api/fetch-url (api/get-sub-hub-url) :method :post :deadline 60 :async? true
                 :headers {"Content-Type" "application/x-www-form-urlencoded"}
                 :payload (.getBytes (str "hub.mode=publish&hub.url="
                                          (java.net.URLEncoder/encode (api/get-feed-url feed)))
                                     "UTF-8"))
  {:status 200})