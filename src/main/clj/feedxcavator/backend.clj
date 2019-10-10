(ns feedxcavator.backend
  (:require [feedxcavator.extraction :as extraction]
            [feedxcavator.core :as core]
            [feedxcavator.code :as code]
            [feedxcavator.log :as log]
            [feedxcavator.db :as db]
            [clojure.string :as str]
            [yaml.core :as yaml]
            [clojure.edn :as edn]
            [feedxcavator.log :as log]
            [appengine-magic.services.mail :as mail]
            [cheshire.core :as json]
            [feedxcavator.code-api :as api])
  (:use hiccup.core
        [clojure.pprint :only [pprint]]))

(defn main-page []
  (core/html-page
    (html [:html
           [:head
            [:title "Feedxcavator"]
            [:link {:rel "stylesheet" :type "text/css" :href "css/goog/common.css"}]
            ;[:link {:rel "stylesheet" :type "text/css" :href "css/goog/tooltip.css"}]
            ;[:link {:rel "stylesheet" :type "text/css" :href "css/goog/flatbutton.css"}]
            [:link {:rel "stylesheet" :type "text/css" :href "css/goog/tab.css"}]
            [:link {:rel "stylesheet" :type "text/css" :href "css/goog/tabbar.css"}]
            [:link {:rel "stylesheet" :type "text/css" :href "css/goog/toolbar.css"}]
            [:link {:rel "stylesheet" :type "text/css" :href "js/jstree/themes/default/style.css"}]
            [:link {:rel "stylesheet" :type "text/css" :href "js/jstree/mod.css"}]
            [:link {:rel "stylesheet" :type "text/css" :href "css/main.css"}]
            [:link {:rel "stylesheet" :type "text/css" :href "css/dark.css"}]
            [:script {:type "text/javascript" :src "js/jquery.js"}]
            [:script {:type "text/javascript" :src "js/jstree/jstree.js"}]
            [:script {:type "text/javascript" :src "js/ace/ace.js"}]
            [:script {:type "text/javascript" :src "js/main.js"}]
            ]
           [:body
            [:div#branding
             [:img {:src "images/logo.png"}]
             [:h1 "Feedxcavator"
                  (when (= core/deployment-type :demo)
                    [:sup {:style "font-size: 50%"} " DEMO"])]]
            [:div#tabbar.goog-tab-bar.goog-tab-bar-top
             [:div#feeds-tab.goog-tab.goog-tab-selected "Feeds"]
             [:div#tasks-code-tab.goog-tab "Tasks"]
             [:div#library-code-tab.goog-tab "Library"]
             [:div#extractors-code-tab.goog-tab "Extractors"]
             [:div#handlers-code-tab.goog-tab "Handlers"]
             [:div#scratch-code-tab.goog-tab "Scratch"]
             [:div#log-tab.goog-tab "Log"]
             [:div#settings-tab.goog-tab "Settings"]]
            [:div.goog-tab-bar-clear]
            [:div#tab-content.goog-tab-content]
            [:script {:type "text/javascript"} "feedxcavator.frontend.main()"]
            ]])))


(defn list-feeds []
  (let [feeds (db/fetch :feed)
        feeds (map #(assoc {} :uuid (:uuid %)
                              :group (:group %)
                              :title (:title %))
                   feeds)]
    (core/text-page (pr-str (sort-by :title feeds)))))

(defn list-task-feeds [task]
  (core/text-page (str/join "\n" (map :title (code/get-task-feeds task)))))

(defn get-feed-url [uuid]
  (if-let [feed (db/fetch :feed uuid)]
    (core/text-page (core/get-feed-url feed))
    (core/page-not-found)))

(defn parse-feed-definition [yaml]
  (let [feed-fields (set (map #(keyword (name %)) db/feed-fields))
        feed (yaml/parse-string yaml)
        standard-fields (filter #(feed-fields (first %)) feed)
        extra-fields (into {} (filter #(not (feed-fields (first %))) feed))
        selectors (into {} (map #(vector (first %)
                                         (let [line (str/trim (second %))]
                                           (if (or (re-matches #"^\[.*\]$" line)
                                                   (re-matches #"^#?\{.*\}$" line))
                                             (read-string line)
                                             (extraction/css-to-enlive line))))
                                (:selectors feed)))]
    (assoc (into {} standard-fields)
      :selectors (when (count selectors) selectors)
      :$$extra (when (count extra-fields) extra-fields))))

(defn get-feed-definition [uuid]
  (let [params-splitter #"(?:params:[^\n]*(?:\n|$)(?:[ ]+[^\n]+(?:\n|$))+)|(?:params:[^\n]*(?:\n|$))"
        feed-definition (:yaml (db/fetch :feed-definition uuid))
        params (db/fetch :feed-params uuid)
        params (when params
                 (yaml/generate-string {:params (:params params)}
                                        :dumper-options (when (not (coll? (:params params)))
                                                          {:flow-style :block})))
        yaml (if params
               (let [parts (str/split feed-definition params-splitter)
                     result (if (> (count parts) 1)
                              (str/join params parts)
                              (str (first parts) params))]
                 (pprint parts)
                 (pprint result)
                 result)
               feed-definition)]
    (core/text-page yaml)))

(defn save-yaml [uuid yaml]
  (let [feed (-> yaml
                 parse-feed-definition
                 (assoc :uuid uuid))
        params (:params (:$$extra feed))
        feed (update-in feed [:$$extra] dissoc :params)
        feed (if (empty? (:$$extra feed))
               (dissoc feed :$$extra)
               feed)]
    (db/store! :feed-definition {:uuid uuid :yaml yaml})
    (db/store! :feed feed)
    (if params
      (db/store! :feed-params {:uuid uuid :params params})
      (db/delete! :feed-params uuid))))

(defn save-feed-definition [request]
  (try
    (let [uuid (get (:multipart-params request) "uuid")
          yaml (get (:multipart-params request) "yaml")]
      (save-yaml uuid yaml)
      (core/text-page "Successfully saved."))
    (catch Exception e
      (core/internal-server-error-message (.getMessage e)))))

(defn create-new-feed []
  (let [uuid (core/generate-uuid)
        yaml
"title: '*Untitled feed'
suffix: untitled-feed
source: https://example.com
#charset: utf-8
#output: rss
#group: group/subgroup
#task: task-name
#selectors:
#  item: div.headline
#  title: h3
#  link: a
#  summary: div.preview
#  image: div.cover img:first-of-type
#  author: div.user-name
#pages:
#  include-source: true
#  path: '/?page=%n'
#  increment: 1
#  start: 2
#  end: 2
#filter:
#  history: true
#  content: title+summary
#  wordfilter: default
#realtime: true
#partition: 100
#extractor: extractor-function-name
#params: [any addiitional data, [123, 456]]
"]
    (save-yaml uuid yaml)
    (core/text-page uuid)))

(defn delete-feed [uuid]
  (db/delete! :feed uuid)
  (db/delete! :feed-definition uuid)
  (db/delete! :feed-params uuid)
  (db/delete! :subscription uuid)
  (core/text-page "OK"))

(defn deliver-feed [suffix]
  (if (and suffix (not (str/blank? suffix)))
    (if-let [feed (if (str/starts-with? suffix "uuid:")
                    (db/fetch :feed (subs suffix 5))
                    (first (db/query :feed (= :suffix suffix))))]
      (try
        (if-let [result (extraction/extract feed)]
          (core/web-page (:content-type result) (:output result))
          (core/no-content))
        (catch Exception e
          (if (core/production?)
            (core/internal-server-error)
            (throw e))))
      (core/page-not-found))
    (core/page-not-found)))

(when (= core/deployment-type :demo)
  (eval '(defn deliver-feed [suffix]
    (core/rss-page
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<rss version=\"2.0\">
  <channel>
    <title>Feedxcavator static demo feed</title>
    <link>https://feedxcavator.appspot.com</link>
    <item>
      <title>Feedxcavator Demo</title>
      <link>https://feedxcavator.appspot.com</link>
      <description>Feedxcavator static demo feed</description>
    </item>
  </channel>
</rss>"))))

(defn test-feed [request]
  (let [uuid (get (:multipart-params request) "uuid")
        yaml (get (:multipart-params request) "yaml")
        feed (-> yaml
                 parse-feed-definition
                 (assoc :uuid uuid))
        feed (if (:extractor feed)
               (assoc feed :extractor (str (:extractor feed) "-test"))
               feed)
        result (extraction/extract (with-meta feed {:testing true}))]
    (core/text-page
      (if (= (:content-type result) "application/rss+xml")
        (core/xml-format (:output result))
        (:output result)))))

(defn get-code [type]
  (core/text-page (:code (db/fetch :code type))))

(defn save-code [request]
  (let [type (get (:multipart-params request) "type")
        code (get (:multipart-params request) "code")]
    (db/store! :code {:type type :code code :timestamp (core/timestamp)})
    (core/text-page (code/compile-user-code type))))

(defn get-log-entries [n from]
  (let [n (when n (Integer/parseInt n))
        from (when from (Integer/parseInt from))]
    (core/text-page (pr-str (log/read-entries n from)))))

(defn clear-log []
  (log/clear-log))

(defn gen-auth-token []
  (let [token (core/generate-random 20)]
    (db/store! :auth-token {:kind "main" :token token})
    (core/text-page token)))

(defn get-auth-token []
  (if-let [token (db/fetch :auth-token "main")]
    (core/text-page (:token token))
    (gen-auth-token)))

(defn redirect [url]
  (core/redirect-to url))

(defn add-filter-regex [request]
  (core/authorized request
                   (let [params (:body request)
                         word-filter (if (:word-filter params) (:word-filter params) "default")
                         regex (:regex params)]
                     (if regex
                       (do
                         (extraction/add-filter-regex word-filter regex)
                         (core/no-content))
                       (core/page-not-found)))))

(defn remove-filter-regex [request]
  (core/authorized request
                   (let [params (:body request)
                         word-filter (if (:word-filter params) (:word-filter params) "default")
                         regex (:regex params)]
                     (if regex
                       (do
                         (extraction/remove-filter-regex word-filter regex)
                         (core/no-content))
                       (core/page-not-found)))))

(defn list-word-filter-words [request]
  (core/authorized request
                   (let [params (:body request)
                         word-filter (if (:word-filter params) (:word-filter params) "default")
                         words (extraction/list-word-filter word-filter)]
                     (if (seq words)
                       (core/json-page (json/generate-string words))
                       (core/page-not-found)))))


(defn list-word-filter [request]
  (core/authorized request
                   (let [filters (map :id (db/fetch :word-filter))]
                     (if (seq filters)
                       (core/json-page (json/generate-string filters))
                       (core/json-page (json/generate-string []))))))

(defn serve-image [name]
  (try
    (let [image (db/fetch-image name)]
      (if image
        (core/web-page (:content-type image) (:bytes image))
        (core/page-not-found)))
    (catch Exception e (core/internal-server-error))))

(defn receive-mail [request]
  (let [message (mail/parse-message request)
        from (:from message)
        from (if (>= (.indexOf from ">") 0)
               (get (re-find #"<([^>]*)>" from) 1)
               from)
        feed (first (db/query :feed (= :source from)))]
    (when feed
      (try
        (let [feed (assoc feed :e-mail message)
              result (extraction/extract (with-meta feed {:background true}))]
          (when result
            (db/store-feed-output! (:uuid feed) result)))
        (catch Exception e
          (.printStackTrace e)))))
  (core/web-page "text/plain" "OK"))

;; sending mail
;(mail/make-message :from (:sender-mail settings)
;                   :to (:recipient-mail settings)
;                   :subject "Subject"
;                   :text-body "Text body")]
;
;(mail/send msg)


(defn service-task-front []
  (code/reset-completed-schedules)
  (core/web-page "text/plain" "OK"))

(defn service-task-background []
  (log/write "executing background service task")
  (code/reset-completed-schedules)
  (let [week-ago (- (core/timestamp) 604800000)
        old-images (db/query :image (> :timestamp week-ago))
        old-log-entries (db/query :log-entry (> :timestamp week-ago))]
    (doseq [image old-images]
      (db/delete-image! (:uuid image)))

    (db/delete*! (map #(db/->entity :log-message {:uuid (:uuid %)}) old-log-entries))
    (db/delete*! old-log-entries))
  (core/web-page "text/plain" "OK"))

(defn restore-database [request]
  (let [edn (get (:multipart-params request) "edn")
        data (edn/read-string (String. (:bytes edn) "utf-8"))]
    (doseq [f (:feeds data)]
      (db/store! :feed f))
    (doseq [f (:feed-definitions data)]
      (db/store! :feed-definition f))
    (doseq [p (:feed-params data)]
      (db/store! :feed-params p))
    (doseq [s (:subscriptions data)]
      (db/store! :subscription s))
    (doseq [t (:auth-tokens data)]
      (db/store! :auth-token t))
    (doseq [o (:objects data)]
      (db/store! :_object o))
    (doseq [w (:word-filters data)]
      (db/store! :word-filter w))
    (doseq [c (:code data)]
      (db/store! :code c))
    (doseq [s (:settings data)]
      (db/store! :settings s)))
  (core/text-page "OK"))

(defn backup-database []
  (core/attachment-page
    "backup.edn"
      (with-out-str
        (pprint
          {
           :feeds            (map #(into {} %) (db/fetch :feed))
           :feed-definitions (map #(into {} %) (db/fetch :feed-definition))
           :feed-params      (map #(into {} %) (db/fetch :feed-params))
           :subscriptions    (map #(into {} %) (db/fetch :subscription))
           :auth-tokens      (map #(into {} %) (db/fetch :auth-token))
           :objects          (map #(into {} %) (db/fetch :_object))
           :word-filters     (map #(into {} %) (db/fetch :word-filter))
           :code             (map #(into {} %) (db/fetch :code))
           :settings         (map #(into {} %) (db/fetch :settings))
           }))))

(defn get-settings []
  (let [settings (-> (into {} (db/fetch :settings "main"))
                     (assoc :version core/app-version))]
    (core/text-page (pr-str settings))))

(defn save-settings [request]
  (let [settings (get (:multipart-params request) "settings")]
    (db/store! :settings (assoc (read-string settings) :kind "main"))))