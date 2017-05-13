(ns feedxcavator.admin
  (:require [net.cgrand.enlive-html :as enlive]
            [feedxcavator.api :as api]))

(defn admin-route []
  (api/html-page
    (api/render
      (enlive/at (enlive/html-resource (api/get-resource-as-stream "admin.html"))))))

(defn backup-database []
  (api/attachment-page "backup.edn" (api/backup-database)))

(defn restore-database [request]
  (api/restore-database (get (:multipart-params request) "edn"))
  (api/redirect-to "/admin"))