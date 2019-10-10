(ns feedxcavator.app
  (:require [feedxcavator.core :as core]
            [feedxcavator.code :as code]
            [feedxcavator.code-user :as code-user] ; required
            [feedxcavator.backend :as backend]
            [feedxcavator.websub :as websub]
            [compojure.handler :as handler]
            [appengine-magic.core :as ae]
            [ring.middleware.json :as ring-json]
            [ring.middleware.multipart-params.byte-array :as ring-byte-array])
  (:use compojure.core))

(def user-code-compiled (atom false))

(defroutes feedxcavator-app-routes
  (GET "/" [] (core/redirect-to "/console"))
  (GET "/console" [] (backend/main-page))

  (GET "/front/feed-url" [uuid] (backend/get-feed-url uuid))
  (GET "/front/list-feeds" [] (backend/list-feeds))
  (GET "/front/list-task-feeds" [task] (backend/list-task-feeds task))
  (GET "/front/create-new-feed" [] (backend/create-new-feed))
  (GET "/front/feed-definition" [uuid] (backend/get-feed-definition uuid))
  (POST "/front/feed-definition" request (backend/save-feed-definition request))
  (POST "/front/test-feed" request (backend/test-feed request))
  (GET "/front/delete-feed" [uuid] (backend/delete-feed uuid))
  (GET "/front/get-code" [type] (backend/get-code type))
  (POST "/front/save-code" request (backend/save-code request))
  (GET "/front/get-log-entries" [n from] (backend/get-log-entries n from))
  (GET "/front/clear-log" [] (backend/clear-log))
  (GET "/front/get-auth-token" [] (backend/get-auth-token))
  (GET "/front/gen-auth-token" [] (backend/gen-auth-token))
  (GET "/front/get-settings" [] (backend/get-settings))
  (POST "/front/save-settings" request (backend/save-settings request))
  (GET "/front/backup-database" [] (backend/backup-database))
  (POST "/front/restore-database" request (backend/restore-database request))

  (ANY "/backend/check-schedules" [] (code/check-schedules))
  (ANY "/backend/service-task-front" [] (backend/service-task-front))
  (ANY "/backend/service-task-background" [] (backend/service-task-background))
  (GET "/backend/run/:task" [task] (code/queue-task task))
  (POST "/backend/execute-task" request (code/execute-queued-task request))

  (POST "/api/wordfilter/add-regex" request (backend/add-filter-regex request))
  (POST "/api/wordfilter/remove-regex" request (backend/remove-filter-regex request))
  (POST "/api/wordfilter/list-words" request (backend/list-word-filter-words request))
  (POST "/api/wordfilter/list" request (backend/list-word-filter request))

  (GET "/feed/:suffix" [suffix] (backend/deliver-feed suffix))
  (ANY "/image/:name" [name] (backend/serve-image name))
  (ANY "/handler/:handler" request (code/execute-handler request))
  (ANY "/redirect/:random/:url" [random url] (backend/redirect url))
  (ANY "/websub" request (websub/hub-action request))

  (ANY "/_ah/mail/*" request (backend/receive-mail request))

  (ANY "*" [] (core/page-not-found)))

(defn context-binder [handler]
  (fn [req]
    (let [server-name (:server-name req)
          is-worker-inst (.startsWith server-name core/worker-url-prefix)]
      (binding [core/*servlet-context* (:servlet-context req)
                core/*remote-addr* (:remote-addr req)
                core/*worker-instance* is-worker-inst
                core/*app-host* (str "https://"
                                     (if is-worker-inst
                                       (.substring server-name (inc (.indexOf server-name ".")))
                                       server-name)
                                     (let [port (:server-port req)]
                                       (when (and port (not= port 80) (not= port 443))
                                         (str ":" port))))

                core/*current-logging-source* nil
                core/*last-http-response* (atom nil)
                core/*last-http-error-code* (atom nil)
                core/*last-http-network-error* (atom nil)]

        (when (not @user-code-compiled)
          (try
            (code/compile-user-code)
            (reset! user-code-compiled true)
            (catch Exception e (.printStackTrace e))))

        (handler req)))))


(def feedxcavator-app-handler
  (ring-json/wrap-json-body
    (handler/site
      (context-binder feedxcavator-app-routes)
      {:multipart {:store (ring-byte-array/byte-array-store)}})
    {:keywords? true :bigdecimals? true}))

(ae/def-appengine-app feedxcavator-app #'feedxcavator-app-handler)