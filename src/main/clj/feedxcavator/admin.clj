(ns feedxcavator.admin
  (:require [net.cgrand.enlive-html :as enlive]
            [feedxcavator.api :as api]
            [feedxcavator.db :as db]))

(defn admin-route []
  (let [settings (db/query-settings)]
    (api/html-page
      (api/render
        (enlive/at (enlive/html-resource (api/get-resource-as-stream "admin.html"))
                   [:#sender-mail] (enlive/set-attr :value (:sender-mail settings))
                   [:#recipient-mail] (enlive/set-attr :value (:recipient-mail settings))
                   [:#report] (enlive/set-attr :value (:report settings)))))))

(defn backup-database-route []
  (api/attachment-page "backup.edn" (db/backup-database)))

(defn restore-database-route [request]
  (db/restore-database (get (:multipart-params request) "edn"))
  (api/redirect-to "/admin"))

(defn admin-store-settings-route [request]
  (db/store-settings! (:params request))
  (api/redirect-to "/admin"))