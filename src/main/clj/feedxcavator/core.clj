;; Feedxcavator (a HTML to RSS converter)
;; (C) 2011 g/christensen (gchristnsn@gmail.com)

(ns feedxcavator.core
  (:require [feedxcavator.api :as api]
            [feedxcavator.editor :as editor]
            [feedxcavator.manager :as manager]
            [feedxcavator.custom :as custom]
            [feedxcavator.admin :as admin]
            [feedxcavator.custom-code :as custom-code]
            [feedxcavator.excavation :as excv]
            [feedxcavator.hub :as hub]
            [compojure.handler :as handler]
            [appengine-magic.core :as ae]
            (ring.middleware [multipart-params :as mp]))
  (:use compojure.core
        [ring.util.mime-type :only [ext-mime-type]]))

(def custom-compiled (atom false))

(defn deliver-route [feed-id]
  (let [feed-settings (when feed-id (api/query-feed feed-id))]
    (if feed-settings
      (try
        (let [result (excv/perform-excavation feed-settings)]
          (apply api/page-found result))
        (catch Exception e
          (if (api/in-debug-env?)
            (throw e)
            (api/internal-server-error))))
      (api/page-not-found))))

(defn deliver-image [id]
  (let [image (api/query-image id)]
    (if image
      (api/page-found (ext-mime-type id) (.getBytes (:data image)))
      (api/page-not-found))))

(defmacro app-routes [& routes]
  (let [all-routes (concat (deref custom/*fetcher-paths*) routes)]
    `(defroutes ~'feedxcavator-app-routes ~@all-routes )))

(app-routes                          
 (GET "/" [] (api/redirect-to "/create"))
 (GET "/create" [] (editor/create-feed-route))
 (GET "/edit" [feed] (editor/edit-feed-route feed))
 (POST "/do-test" request (editor/do-test-route request))
 (POST "/do-create" request (editor/do-create-route request))
 (GET "/deliver" [feed] (deliver-route feed))
 (ANY "/hub" request (hub/post-action request))
 (GET "/publish" [feed] (hub/publish-notify feed))
 (GET "/image" [id] (deliver-image id))
 (GET "/delete" [feed] (manager/delete-route feed))
 (GET "/double" [feed] (manager/duplicate-route feed))
 (GET "/manage" [] (manager/manage-route))
 (GET "/custom" [] (custom/custom-route))
 (POST "/retreive-custom" request (custom/retreive-custom-route request))
 (POST "/save-custom" request (custom/save-custom-route request))
 (POST "/store-external-data" [feed-id data] (custom/store-external-data feed-id data))
 (POST "/store-encoded-external-data" [feed-id data] (custom/store-encoded-external-data feed-id data))
 (GET "/report-external-errors" [date] (custom/report-external-errors date))
 (GET "/clear-realtime-history" [] (custom/clear-realtime-history))
 (ANY "/service-task" [] (custom/service-task))
 (ANY "/clear-data" [] (custom/clear-data))
 (GET "/robots.txt" [] (api/text-page "User-agent: *"))
 (GET "/proxify*" [url referer cookie] (api/proxify url referer cookie))
 (GET "/akiba-search" [keywords] ((ns-resolve 'feedxcavator.custom-code 'akiba-search) keywords))
 (GET "/admin" [] (admin/admin-route))
 (GET "/backup" [] (admin/backup-database))
 (POST "/restore" [edn] (admin/restore-database edn))
 (ANY "*" [] (api/page-not-found)))


(defn context-binder [handler]
  (fn [req]
    (binding [api/*servlet-context* (:servlet-context req)
              api/*remote-addr* (:remote-addr req)
              api/*app-host* (str #_(name (:scheme req)) "https://"
                                  (let [server-name (:server-name req)]
                                    (if (.startsWith server-name "worker.")
                                      (.substring server-name (inc (.indexOf server-name ".")))
                                      server-name))
                                  (let [port (:server-port req)]
                                        (when (and port (not= port 80))
                                          (str ":" port))))]
    (when (not @custom-compiled)
      (try
        (binding [*ns* (find-ns 'feedxcavator.custom)]
          (load-string (api/query-custom-code custom/+current+))
          (swap! custom-compiled (fn [a] true)))
        (catch Exception e (println (.getStackTrace e)))))
      
      (handler req))))

(def feedxcavator-app-handler (handler/site
                                (context-binder feedxcavator-app-routes)))

(ae/def-appengine-app feedxcavator-app #'feedxcavator-app-handler)