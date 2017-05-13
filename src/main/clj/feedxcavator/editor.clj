;; Feedxcavator (a HTML to RSS converter)
;; (C) 2011 g/christensen (gchristnsn@gmail.com)

(ns feedxcavator.editor
  (:require [feedxcavator.api :as api]
            [feedxcavator.excavation :as excv]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as enlive]
            [appengine-magic.services.user])
  (:use clojure.tools.macro))

(defn editor-template []
  (enlive/at ; remove nodes not necessary in a specific type of installation
   (enlive/html-resource (api/get-resource-as-stream "editor.html"))
   
   [#{:#custom-excavator-label :#custom-excavator :#custom-params-label :#custom-params
      :#button-space}]
   (fn [node]
     (when (not api/+public-deploy+) node))

   [#{:#recaptcha-label :#recaptcha-script :#recaptcha :#public-warning}]
   (fn [node]
     (when api/+public-deploy+ node))))
   

(defn create-feed-route []
  (api/html-page
   (api/render (enlive/transform (editor-template) 
                                 [:#service-links] ; remove service links if public
                                 (fn [node]
                                   (when (not api/+public-deploy+) node))))))

(defn fill-node [node value]
  (if (= :input (:tag node))
    (case (:type (:attrs node))
      "checkbox"
      (if value
        ((enlive/set-attr :checked  "checked") node)
        ((enlive/remove-attr :checked) node))
      ((enlive/set-attr :value value) node))
    ((enlive/content value) node)))

(defmacro fill-for-edit [field-set settings doc & body]
  (let [gsettings (gensym)]
    `(let [~gsettings ~settings]
       (-> ~doc
           ~@(for [field (mexpand field-set)]                                    
               `(enlive/transform [[:* (enlive/attr= :name ~(name field))]]
                                  (fn [node#]
                                    (fill-node node# (~(keyword field) ~gsettings)))))
           ~@body))))
                
(defn edit-feed-route [feed-id]
  (let [feed-settings (when feed-id (api/query-feed feed-id))]
    (if feed-settings
      (api/html-page
       (api/render
        (fill-for-edit api/feed-fields feed-settings
                       (fill-for-edit api/selector-fields (:selectors feed-settings) (editor-template)
                                      (enlive/transform [:#service-links]
                                                        (fn [node]
                                                          (when (not api/+public-deploy+) node)))
                                      (enlive/transform [:#feed-link]
                                                        (enlive/do->
                                                         (enlive/set-attr :title (:feed-title feed-settings))
                                                         (enlive/set-attr :href (str api/*app-host*
                                                                                     api/+feed-url-base+
                                                                                     (:uuid feed-settings)))))
                                      (enlive/transform [:#save]
                                                        (enlive/content "Save"))))))
      (api/page-not-found))))
  
(defn css-to-enlive [line]
  (let [line (str/replace line #">" " > ")
        line (str/trim (str/replace line #"[ ]+" " "))
        tokens (str/split line #" ")
        tokens (for [token tokens]
                 (let [token (str ":" token)
                       ;; filter out attribute matchers, only existence check (attr?),
                       ;; full comparison (attr=) and substring check (attr-contains) are supported
                       token (if (re-matches #".*\[.*\].*" token)
                               (str/replace token #"(.*)\[(.*)\](.*)"
                                            (fn [match]
                                              (str "[" (nth match 1) " (attr"
                                                   (str/replace (nth match 2) #"([^=*~|\^$]+)(?:(.?=)(.*))?"
                                                                (fn [match]
                                                                  (if (str/blank? (get match 2))
                                                                    (str "? :" (get match 1) ")")
                                                                    (str (case (get match 2)
                                                                           "=" "="
                                                                           "*=" "-contains"
                                                                           :default "=")
                                                                         " :" (get match 1) " "
                                                                         (if (re-matches #"^\".*\"$"
                                                                                         (get match 3))
                                                                           (get match 3)
                                                                           (str "\"" (get match 3) "\"")) ")"))))
                                                   "]" (get match 3))))
                               token)
                       ;; filter out pseudoclasses, only parameterless and pseudoclasses with single
                       ;; numeric parameter are supported
                       token (if (re-matches #":.+:.+" token)
                               (str/replace token #":(.+):([^(]+)(?:\((\d+)\))?"
                                            (fn [match]
                                              (if (str/blank? (get match 3))
                                                (str ":" (get match 1) " :> " (get match 2))
                                                (str "[:" (get match 1) " (" (get match 2) " " (get match 3)
                                                     ")]"))))
                               token)]
                   token))]
    (read-string (str "[" (apply str (interpose " " tokens)) "]"))))

(defn read-selectors [selectors]
  (let [selectors-kv
        (for [kv selectors]
          (let [selector-set (second kv)]
            (if selector-set
              (let [selector-lines (str/split selector-set #"\n")
                    selectors (for [line selector-lines]
                                (let [line (str/trim line)]
                                  (if (or (re-matches #"^\[.*\]$" line)
                                          (re-matches #"^#?\{.*\}$" line))
                                    (read-string line)
                                    (css-to-enlive line))))]
                [(first kv) (apply vector selectors)])
              [(first kv) nil])))]         
    (apply hash-map (apply concat selectors-kv))))

(defn read-feed-settings [request]
  (let [feed-settings (read-string (slurp (:body request)))
        ;; set up target-url protocol
        target-url (:target-url feed-settings)
        feed-settings (if (re-matches #"^https?://.*" target-url)
                        feed-settings
                        (assoc feed-settings :target-url (str "http://" target-url)))
        selectors (read-selectors (:selectors feed-settings))]
    (assoc feed-settings :enlive-selectors selectors)))

;; a straightforward compensation for not so perfect enlive output
(defn fix-glued-tags [feed]
  (case (first feed)
    "application/rss+xml"
    (.replace (str (second feed)) "</item><item>" "</item>\n\n&nbsp;&nbsp;&nbsp;&nbsp;<item>")
    "application/atom+xml"
    (.replace (str (second feed)) "</entry><entry>" "</entry>\n\n&nbsp;&nbsp;&nbsp;&nbsp;<entry>")
    (second feed)))

(defn html-escape-feed [feed]
  (let [feed (fix-glued-tags feed)
        feed (str/escape (api/sanitize feed) {\space "&nbsp;"})

        feed (.replace feed "\n" "<br/>")]
    (str "<div id=\"feed-content\">" feed "</div>")))

(defn perform-test [feed-settings]
  (let [;; turn off read headline filtering
        feed-settings (assoc feed-settings :remember-recent nil)]
      (let [feed-settings (if (str/blank? (:custom-excavator feed-settings))
                            feed-settings
                            (assoc feed-settings :custom-excavator
                                                  (str (:custom-excavator feed-settings) "-test")))
            feed (excv/perform-excavation feed-settings)]
        (cond
         (:out-of-sync (meta feed))
         ;; a little hack to permit raw feed transfoming excavators such as DiggAtomCommentsBypasser
         (if (not= "ignore" (:headline (:selectors feed-settings)))
           (throw (Exception. "Probably, selectors do not match.")))
         :default (str (:n-articles (meta feed)) " headlines extracted from the target location:<br/><br/>"
                       (html-escape-feed feed))))))

(defmacro with-exception-msg [& body] `(do ~@body))

(defn do-test-route [request]
  (let [feed-settings (read-feed-settings request)]
    (api/text-page
     (with-exception-msg
       (perform-test feed-settings)))))
     
(enlive/defsnippet create-response (api/get-resource-as-stream "create-response.html")
  [enlive/root] [feed-settings]
  [:#feed-id]
  (enlive/content (:uuid feed-settings))
  [:a#feed-link]
  (enlive/set-attr :href (str api/+feed-url-base+ (:uuid feed-settings)))
  [:a#edit-link]
  (enlive/set-attr :href (str api/+edit-url-base+ (:uuid feed-settings))))

(defn do-create-route [request]
  (let [feed-settings (read-feed-settings request)]
    (api/text-page
     (with-exception-msg
       (when (not (api/confirmation-valid? (:response feed-settings) (:challenge feed-settings)))
         (throw (Exception. "Invalid confirmation.")))
;;       (perform-test feed-settings)
       (let [feed-id (:uuid feed-settings)
             feed-settings (if feed-id
                             feed-settings
                             (assoc feed-settings :uuid (api/get-uuid)))
             the-feed (api/store-feed! (api/cons-feed-from-map feed-settings))]
         (if feed-id       
           "Saved."
           (api/render (create-response the-feed))))))))
