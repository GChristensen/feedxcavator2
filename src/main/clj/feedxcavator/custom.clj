(ns feedxcavator.custom
  (:require [feedxcavator.api :as api]
            [clojure.string :as str]
            [feedxcavator.excavation :as excv]
            [net.cgrand.enlive-html :as enlive]
            [appengine-magic.services.mail :as mail])
  (:use clojure.tools.macro))

(def ^:dynamic *fetcher-tasks* (atom {}))
(def ^:dynamic *schedules* (atom []))
(def ^:dynamic *completed-schedules* (atom []))

(defn $cleanup-tasks []
  (reset! *fetcher-tasks* {})
  (reset! *schedules* []))

(defn fetch-feeds [feeds]
  (let [feeds (filter #(some (fn [s#] (>= (.indexOf (:feed-title %) s#) 0)) feeds) (api/get-all-feeds))]
    (doseq [f feeds]
      (try
        (let [result (excv/perform-excavation (assoc f :background-fetching true))]
          (when result
            (api/store-rss! (:uuid f) result)))
        (catch Exception e
          (println (.getMessage e)))))
    (api/page-found "text/plain" "OK")))

(defmacro deftask [task-name [& feeds] ]
  `(swap! *fetcher-tasks* assoc ~(name task-name)
                          {
                           :queue-fn   (fn [] (api/named-queue-add! "fetch-queue" "/task" ~(name task-name)))
                           :fetcher-fn (fn [] (fetch-feeds #{~@feeds}))
                           }
                          ))

(defmacro schedule [task hours mins]
  `(when (not api/*worker-instance*)
     (swap! *schedules* conj {:task ~(name task) :hours ~hours :mins ~mins})))

(defn in-prev-5min-range [inst t]
  (and (>= inst (- t 5)) (<= inst t)))

(defn check-tasks-route []
  (let [date (java.util.Date.)]
    (doseq [s @*schedules*]
      (when (and (not (some #(= % s) @*completed-schedules*))
                 (= (:hours s) (.getHours date))
                 (in-prev-5min-range (:mins s) (.getMinutes date)))
        (swap! *completed-schedules* conj s)
        ((:queue-fn (@*fetcher-tasks* (:task s)))))))
  (api/page-found "text/plain" "OK"))

(defn custom-task-route [request]
  (try
     ((:fetcher-fn (@*fetcher-tasks* (slurp (:body request)))))
     (catch Exception e
       (.printStackTrace e)))
  (api/page-found "text/plain" "OK"))

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

(defmacro defbackground [fun-name [feed-settings params] & body]
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
  (reset! *completed-schedules* [])
  (let [now (api/timestamp)
        week-ago (- now 604800)]
    (api/delete-images! week-ago))
  (api/page-found "text/plain" "OK"))

(defn clear-data []
  (let [now (api/timestamp)]
    (api/delete-images! now))
  (api/page-found "text/plain" "OK"))

(defn custom-code-route []
  (if api/+public-deploy+
    (api/page-not-found)
    (api/html-page
     (api/render
      ;(enlive/transform
       (enlive/html-resource (api/get-resource-as-stream "custom.html"))))));)

(defn retreive-custom-code-route [request]
  (if api/+public-deploy+
    (api/page-not-found)
    (let [state (slurp (:body request))]
        (api/html-page (api/query-custom-code)))))

(defn save-custom-code-route [request]
  (if api/+public-deploy+
    (api/page-not-found)
    (let [code (slurp (:body request))]
      (api/store-custom-code! code)
      (binding [*ns* (find-ns 'feedxcavator.custom)]
        (load-string (api/set-custom-ns code)))
      (api/html-page ""))))

(defn run-task-route [task]
  (try
    ((:queue-fn (@*fetcher-tasks* task)))
    (api/page-found "text/plain" (str task " queued"))
    (catch Exception e
      (api/page-found "text/plain" "ERROR"))))

(defn external-fetching-route []
  ((:queue-fn (@*fetcher-tasks* "fetch-external")))
  (api/page-found "text/plain" "OK"))

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
    (let [settings (api/query-settings)
          msg (mail/make-message :from (:sender-mail settings)
                                 :to (:recipient-mail settings)
                                 :subject "RSS Errors"
                                 :text-body (format (:report-url settings) date))]
                                 
      (mail/send msg)
      (api/html-page ""))))

(defn clear-realtime-history []
  (api/delete-fetcher-history!)
  (api/html-page ""))