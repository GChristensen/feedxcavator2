(ns feedxcavator.admin
  (:require [net.cgrand.enlive-html :as enlive]
            [feedxcavator.api :as api]))

(defn admin-route []
  (let [settings (api/query-settings)]
    (api/html-page
      (api/render
        (enlive/at (enlive/html-resource (api/get-resource-as-stream "admin.html"))
                   [:#sender-mail] (enlive/set-attr :value (:sender-mail settings))
                   [:#recipient-mail] (enlive/set-attr :value (:recipient-mail settings))
                   [:#report-url] (enlive/set-attr :value (:report-url settings)))))))

(defn backup-database []
  (api/attachment-page "backup.edn" (api/backup-database)))

(defn restore-database [request]
  (api/restore-database (get (:multipart-params request) "edn"))
  (api/redirect-to "/admin"))

(defn admin-store-settings-route [request]
  (api/store-settings! (:params request))
  (api/redirect-to "/admin"))