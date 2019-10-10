(ns feedxcavator.htmlunit
  (:require [clojure.string :as str])
  (:import [com.gargoylesoftware.htmlunit BrowserVersion WebClient CookieManager
                                          HttpMethod WebRequest]
           [com.gargoylesoftware.htmlunit.util FalsifyingWebConnection]
           [java.net URL]))

(defn htmlunit-client [& {:keys [js-timeout filter-js] :or {js-timeout 30000}}]
  (let [client (WebClient. BrowserVersion/CHROME)
        cookie-manager (proxy [CookieManager] [] (getPort [url] 80))]

    (doto client
      (.setJavaScriptTimeout js-timeout)
      (.setCookieManager cookie-manager))

    (when filter-js
      (.setWebConnection client
                         (proxy [FalsifyingWebConnection] [client]
                           (getResponse [request]
                             (let [host (.toLowerCase (.getHost (.getUrl request)))]
                               (if (some #(str/includes? host (str/lower-case %)) filter-js)
                                 (.createWebResponse request "" "application/javascript")
                                 (proxy-super getResponse request)))))))

    (doto (.getOptions client)
      (.setCssEnabled false)
      (.setJavaScriptEnabled false)
      (.setThrowExceptionOnFailingStatusCode false)
      (.setRedirectEnabled true))

    (.setMaxSize (.getCache client) 0)

    client))

(defn solve-cloudflare [client url & {:keys [timeout] :or {timeout 7000}}]
  (let [page (.getPage client url)]
    (when (.querySelector page ".cf-browser-verification")
      (.setJavaScriptEnabled (.getOptions client) true)
      (let [page (.getPage client url)]
        (locking page
          (.wait page timeout)))
      (.setJavaScriptEnabled (.getOptions client) false))))

(defn htmlunit-fetch-url [client url & {:keys [method headers] :or {method :get}}]
  (let [method (cond (= method :post) HttpMethod/POST
                     :else HttpMethod/GET)
        request (WebRequest. (URL. url) method)]
    (doseq [header headers]
      (.setAdditionalHeader request (first header) (second header)))
    (let [page (.getPage client request)
          response (.getWebResponse page)]
      (.getContentAsString response))))
