(ns feedxcavator.custom
  (:require [feedxcavator.api :as api]
            [clojure.string :as str]
            [feedxcavator.excavation :as excv]
            [net.cgrand.enlive-html :as enlive]
            [appengine-magic.services.mail :as mail])
  (:use clojure.tools.macro))

(def ^:const +current+ "current")

(def ^:dynamic *fetcher-paths* (atom []))

(defn deffetcher-helper 
  ([group-name] (deffetcher-helper group-name nil))
  ([group-name external-feeds]
     (let [web-fun (str (name group-name) "-fetching")
           worker-fun (str "do-" (name group-name) "-fetching")]
       (swap! *fetcher-paths* conj `(~'GET ~(str "/" web-fun) [] (~(symbol (str "custom/" web-fun)))))
       (swap! *fetcher-paths* conj `(~'ANY ~(str "/" worker-fun) []  (~(symbol (str "custom/" worker-fun)))))
       `(do
          (defn ~(symbol web-fun) []
            (api/queue-add! ~(str "/" worker-fun) "")
            (api/page-found "text/plain" "OK"))

          (defn ~(symbol worker-fun) []
            (let [feeds# (filter #(some (fn [s#] (>= (.indexOf (:feed-title %) s#) 0)) 
                                       ~(if external-feeds
                                          `#{~external-feeds}
                                          `(deref (resolve '~(symbol (str excv/+custom-code-ns+ "/*" 
                                                                          (name group-name) "-feeds*"))))))
                                (api/get-all-feeds))]
              (doseq [f# feeds#]
                (try
                  (let [feed# (excv/perform-excavation 
                              (assoc f# :background-fetching true))]
                    (when feed#
                      (api/store-rss! (:uuid f#) feed#)))
                  (catch  Exception e#
                    (println (.getMessage e#)))))
              (api/page-found "text/plain" "OK")))))))

(defmacro deffetcher [& args] (apply deffetcher-helper args))

(deffetcher periodic)
(deffetcher external)
(deffetcher x-forums)
(deffetcher daily)
(deffetcher rt "rutracker")

(defmacro defextractor [fun-name [feed-settings params] & body]
  (let [extractor-fun (gensym)]
    `(do
       (defn ~extractor-fun [~feed-settings ~params]
         (println ~(str "executing " (name fun-name)))

         (let [headlines# (do ~@body)]
           (if (not (empty? headlines#))
             ["application/rss+xml" (~'make-rss-feed headlines# ~feed-settings)]
             (throw (Exception. "Nothing extracted")))))
       (defn ~(symbol (str (name fun-name) "-test")) [~feed-settings ~params]
         (~extractor-fun ~feed-settings ~params))
       (defn ~fun-name [~feed-settings ~params]
         (~extractor-fun ~feed-settings ~params)))))

(defmacro defextractor-bg [fun-name [feed-settings params] & body]
  (let [extractor-fun (gensym)]
    `(do
       (defn ~extractor-fun [~feed-settings ~params]
         (println ~(str "executing " (name fun-name)))

         (if (:background-fetching ~feed-settings)
           (let [headlines# (do ~@body)]
             (if (not (empty? headlines#))
               (~'make-rss-feed headlines# ~feed-settings)
               (throw (Exception. "Nothing extracted"))))
           ["application/rss+xml" (:content (api/query-stored-rss (:uuid ~feed-settings)))]))
       (defextractor ~fun-name [~feed-settings ~params] ~@body)
       (defn ~fun-name [~feed-settings ~params]
         (~extractor-fun ~feed-settings ~params)))))

(defn service-task []
  (let [now (api/timestamp)
        week-ago (- now 604800)]
    (api/delete-images! week-ago))
  (api/page-found "text/plain" "OK"))

(defn clear-data []
  (let [now (api/timestamp)]
    (api/delete-images! now))
  (api/page-found "text/plain" "OK"))

(def custom-template )

(defn custom-route []
  (if api/+public-deploy+
    (api/page-not-found)
    (api/html-page
     (api/render
      ;(enlive/transform
       (enlive/html-resource (api/get-resource-as-stream "custom.html"))))));)

(defn retreive-custom-route [request]
  (if api/+public-deploy+
    (api/page-not-found)
    (let [state (slurp (:body request))]
        (api/html-page (api/query-custom-code state)))))

(defn save-custom-route [request]
  (if api/+public-deploy+
    (api/page-not-found)
    (let [code (slurp (:body request))]
      (api/store-custom-code! +current+ code)
      (binding [*ns* (find-ns 'feedxcavator.custom)]
        (load-string code))
      (api/html-page ""))))

(defn store-external-data [feed-id data]
  (if api/+public-deploy+
    (api/page-not-found)
    (when (api/query-feed feed-id)
      (api/store-external-data! feed-id data)
      (api/html-page ""))))

(defn store-encoded-external-data [feed-id data]
  (if api/+public-deploy+
    (api/page-not-found)
    (when (api/query-feed feed-id)
      (api/store-external-data! feed-id (api/base64dec-unsafe data))
      (api/html-page ""))))

(defn report-external-errors [date]
  (if api/+public-deploy+
    (api/page-not-found)
    (let [msg (mail/make-message :from "error@myxcavator.appspotmail.com"
                                 :to "meta.person@gmail.com"
                                 :subject "RSS Errors"
                                 :text-body
                                 (str "Errors in the last log: http://gateway:81/logs/log-" date ".html"))]
                                 
      (mail/send msg)
      (api/html-page ""))))

(defn clear-realtime-history []
  (api/delete-fetcher-history!)
  (api/html-page ""))