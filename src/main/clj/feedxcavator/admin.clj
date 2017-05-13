(ns feedxcavator.admin
  (:require [net.cgrand.enlive-html :as enlive]
            [feedxcavator.api :as api]))

(defn admin-route []
  (api/html-page
    (api/render
      (enlive/at (enlive/html-resource (api/get-resource-as-stream "admin.html"))))))

(defn backup-database []
  (api/html-page (api/backup-database)))

(defn restore-database [edn]
  (api/restore-database edn)
  (api/redirect-to "/admin"))