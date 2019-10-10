(ns feedxcavator.ajax
  (:require [ajax.core :refer [GET POST]]
            [clojure.string :as str]
            ;[feedxcavator.mock-backend :as demo]
            )
  (:use [cljs.reader :only [read-string]]))

(defn get-text
  ([url handler]
   (GET url {:handler handler :error-handler handler}))
  ([url params handler]
   (GET url {:params params :handler handler :error-handler handler})))

(defn get-edn
  ([url params handler]
   (GET url {:params params
             :handler (fn [r]
                        (handler (read-string r)))}))
  ([url handler]
   (get-edn url nil handler)))

(defn post-multipart [url params handler]
  (let [form-data (js/FormData.)]
    (doseq [p params]
      (.append form-data (name (first p)) (second p)))
    (POST url {:handler handler
               :error-handler handler
               :params  params
               :body    form-data})))

(defn extract-server-error [response-text]
  (let [error (-> response-text
                  (str/replace "<h3>Caused by:</h3>" "\n\n<br><h3>Caused by:</h3><br>\n"))
        doc (.createHTMLDocument (.-implementation js/document) "")]
    (set! (.-innerHTML (.-documentElement doc)) error)
    (let [content (str/trim (.-textContent (.-body doc)))]
      (-> content
          (str/replace #"HTTP ERROR 500\n.*?Reason:" "Error:")
          (str/replace #"^(?:.|\n)*?Caused by:\s*" "")
          (str/replace #"\nPowered by Jetty://.*(?:\n|$)" "")))))


#_(do
  (def get-text demo/get-text)
  (def get-edn demo/get-edn)
  (def post-multipart demo/post-multipart))