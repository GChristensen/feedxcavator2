(ns feedxcavator.excavation
  (:require [feedxcavator.api :as api]
            [feedxcavator.db :as db]
            [clojure.string :as str]
            [clj-time.core :as tm]
            [clj-time.format :as fmt])
  (:use [ring.util.mime-type :only [ext-mime-type]]
         net.cgrand.enlive-html
         clojure.walk))

(def ^:const +xml-header+ "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")

(defmacro remove-when [cond]
  `(fn [node#]
     (if ~cond 
       nil
       node#)))

(defn filter-read-articles
  "Filter out articles feedxcavator already seen on this feed (may produce unexpected results)."
  ([headlines feed-settings]
     (filter-read-articles headlines nil feed-settings))

  ;; id extractor may be useful in custom excavators if different full article link structure
  ;; is used in headlines
  ([headlines id-extractor feed-settings]
     (let [remember-recent (:remember-recent feed-settings)
           recent-article (:recent-article feed-settings)]
       (if (and (seq headlines) remember-recent)
         (let [id-extractor (or id-extractor (fn [h] (:link h)))
               feed-settings (assoc feed-settings 
                               :background-fetching nil
                               :recent-article (id-extractor (first headlines)))]
           (db/store-feed! feed-settings)
           (take-while #(not= (id-extractor %) recent-article) headlines))
         headlines))))

(defmacro error-headline [feed title description & body]
  `(at ~feed
       ~title (content "Error")
       ~description
       (content "Probably Feedxcavator selectors are out of sync with the resource markup.")
       ~@body))

(defn make-rss-feed 
  "Transform the list of hash-maps with extracted headline data to a RSS feed XML representation."
  [headlines feed-settings]
  (let [feed-w-head (at (xml-resource (api/get-resource-as-stream "rss.xml"))
                        [:rss] (set-attr :xml:base (api/sanitize (:target-url feed-settings)))
                        [:channel :> :link#main-link] #(at %
                                                         [:link#main-link]
                                                         (do->
                                                          (remove-attr :id)
                                                          (content (:target-url feed-settings))))
                        [:channel :> [:atom:link (attr= :rel "hub")]]
                          (do->
                           (set-attr :href (api/get-sub-hub-url))
                           (remove-when (not (:realtime feed-settings))))
                        [:channel :> [:atom:link (attr= :rel "self")]] 
                          (set-attr :href (api/get-feed-url (:uuid feed-settings)))
                        [:channel :> :title] (content (:feed-title feed-settings))
                        [:channel :> :pubDate] (content (fmt/unparse (:rfc822 fmt/formatters) (tm/now)))
                        [:channel :> :lastBuildDate] 
                            (content (fmt/unparse (:rfc822 fmt/formatters) (tm/now))))]
    (let [feed (if (:out-of-sync (meta headlines))
                 (error-headline feed-w-head
                                 [:channel :> :item :> :title] 
                                 [:channel :> :item :> :description]
                                 [:channel :> :item :> :link] (content (:target-url feed-settings)))
                 (transform feed-w-head [:channel :> :item]
                            (clone-for [headline headlines]
                                       [:title] (content (:title headline))
                                       [:description] (content (:summary headline))
                                       [:link] (content (:link headline))
                                       [:guid] #(if (:realtime feed-settings)
                                                  ((content (:link headline)) %)
                                                  nil)
                                       [:author] (content (:author headline))
                                       [:enclosure] (when (:image headline)
                                                      (do->
                                                       (set-attr :url (:image headline))
                                                       (set-attr :type (if (:image-type headline)
                                                                         (:image-type headline)
                                                                         (ext-mime-type 
                                                                          (:image headline)))))))))]
      (apply str (cons +xml-header+ (emit* feed))))))

(defprotocol HTMLExcavator
  "HTML to feed conversion routine protocol"
  (excavate [this feed-settings]
    "Main excavation routine. Should return a vector with content type as the first vector element
and string feed data as the second."))

(defn default-excavator [feed-settings]
  (let [response (api/fetch-url (:target-url feed-settings))
        doc-tree (api/resp->enlive response feed-settings)]
    (if doc-tree
      (let [headlines (-> (api/apply-selectors doc-tree feed-settings)
                          (filter-read-articles feed-settings))]
        (api/with-meta-from headlines ; pass meta to the result vector (needed by editor)
                            ["application/rss+xml" (make-rss-feed headlines feed-settings)]))
      (throw (Exception. "Target page not found")))))

(defn perform-excavation 
  "Performs feed data extraction and conversion to RSS based on the given feed settings.
Custom excavator will be used if specified in the feed settings."
  [feed-settings]
  (let [custom-excavator (:custom-excavator feed-settings)
        namespace (when custom-excavator (last (re-find #"(.*?)[\.\/][^.\/]+$" custom-excavator)))]
    (if (not (str/blank? custom-excavator))
      (let [custom-params (:custom-params feed-settings)
            custom-params (if (str/blank? custom-params) [] (read-string custom-params))]
        (require (read-string api/+custom-ns+))
        (if (>= (.indexOf custom-excavator "/") 0)
          (let [callable (resolve (read-string custom-excavator))]
            (callable feed-settings custom-params))
          (let [callable (resolve (read-string (str api/+custom-ns+ "/" custom-excavator)))]
            (callable feed-settings custom-params))))
      (default-excavator feed-settings))))
