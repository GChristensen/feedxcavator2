;; Feedxcavator (a HTML to RSS converter)
;; (C) 2011 g/christensen (gchristnsn@gmail.com)

(ns feedxcavator.editor
  (:require
   [clojure.string :as str]
   [goog.ui.decorate :as decorate]
   [goog.dom :as dom]
   [goog.object :as goog-object]
   [goog.events :as events])
  (:import
   goog.events.EventType
   goog.ui.Tooltip
   goog.ui.Button
   goog.ui.FlatButtonRenderer
   goog.net.XhrIo
   goog.structs.Map))

(defn post-data [url data callback]
  (let [headers (goog.structs.Map.)]
    (.set headers "Content-Type" "text/plain")
    (goog.net.XhrIo/send url callback "POST" data headers)))

(defn show-output [content]
  (show-loading false)
  (let [output (dom/getElement "output")]
    (set! (.-innerHTML output) content)))

(defn show-loading [show]
  (let [loading (dom/getElement "loading")]   
    (if show
      (do
        (show-output "")
        (set! (.-innerHTML loading) "<img src=\"images/loading.gif\"/>"))
      (set! (.-innerHTML loading) ""))))
  
(defn collect-data []
  (let [mk-pred (fn [name] (fn [node] (= name (str/lower-case (.-nodeName node)))))
        mk-data-map (fn [node-list]
                      (loop [nodes node-list data {}]
                        (let [node (first nodes)]
                          (if node
                            (let [value (if (= (.-type node) "checkbox")
                                          (.-checked node)
                                          (.-value node))
                                  value (if (= (goog/typeOf value) "string")
                                          (when (not (str/blank? value)) (str/trim value))
                                          value)]
                              (recur (next nodes) (assoc data (keyword (.-name node))
                                                         value)))
                            data))))
        settings (dom/findNodes (dom/getElement "settings") (mk-pred "input"))
        selectors (dom/findNodes (dom/getElement "selectors") (mk-pred "textarea"))]
    (assoc (mk-data-map settings) :selectors (mk-data-map selectors))))

(defn validate-data [data]
  (let [selectors (:selectors data)]
    (if (and (:feed-title data)
             #_(:target-url data)
             #_(:headline selectors)
             #_(:title selectors)
             #_(:link selectors))
      data
      (js/alert "Please fill all mandatory fields."))))

(defn do-test-callback [e]
  (let [xhr (.-target e)
        response (. xhr (getResponseText))]
    (show-output response)))

(defn test-btn-handler [e]
  (let [data (validate-data (collect-data))]
    (when data
      (show-loading true)
      (post-data "/do-test" (pr-str data) do-test-callback))))

(defn do-create-callback [e]
  (let [xhr (.-target e)
        response (. xhr (getResponseText))]
    (show-output response)))

(defn ^:export get-link-btn-handler [e]
  (let [data (validate-data (collect-data))]
    (when data
      (show-loading true)
      (let [data (if (dom/getElement "recaptcha")
                   (-> data
                       (assoc :challenge (. js/Recaptcha (get-challenge)))
                       (assoc :response (. js/Recaptcha (get-response))))
                   data)]
        (when (dom/getElement "recaptcha") (. js/Recaptcha (reload)))
        (post-data "/do-create" (pr-str data) do-create-callback)))))

(defn ^:export main []
  (goog.ui.Tooltip. "article-lbl" "Headline encompassing element which contains other
heandline elements such as link and title. All other <br/>selectors are applied relative to this element.
You may select elements with more than one set of selectors,<br/> each set is placed on a distinct line. Selectors from the first line of each field will be used to select one group<br/> of headlines and the
corresponding member elements and so on. If there is only one line, it will be used in<br/>the all cases.")
  (goog.ui.Tooltip. "title-lbl" "An element containing headline title text.")
  (goog.ui.Tooltip. "link-lbl" "An 'A' HTML element which 'href' attribute contains a link to the full article.")
  (goog.ui.Tooltip. "summary-lbl" "An element containing short summary (text or html) of the article.")
  (goog.ui.Tooltip. "image-lbl" "An 'IMG' HTML element which 'src' attribute contains a link to an article image or photo.")
  (goog.ui.Tooltip. "encoding-lbl" "Specify this only if encoding of the target HTML-page is not provided by the HTTP protocol.")
  (when (dom/getElement "recaptcha-lbl")
    (goog.ui.Tooltip. "recaptcha-lbl" "It's not necessary to enter confirmation while testing."))
  (when (dom/getElement "excavator-lbl")
    (goog.ui.Tooltip. "excavator-lbl" "Fully qualified clojure type name of a custom excavator."))
  (when (dom/getElement "parameters-lbl")
    (goog.ui.Tooltip. "parameters-lbl" "Space separated list of custom excavator constructor parameters (only numbers, strings and keywords are permitted)."))
  
  (goog.ui.Tooltip. "recent-article-lbl" "Remember the most recent article and do not include headlines below it to the feed (may produce unexpected results).")

  (def test-btn (goog.ui.decorate (dom/getElement "test")))

  (events/listen test-btn
                 goog.ui.Component.EventType/ACTION
                 test-btn-handler)
  
  (def get-link-btn (goog.ui.decorate (dom/getElement "save")))

  (events/listen get-link-btn
                 goog.ui.Component.EventType/ACTION
                 get-link-btn-handler))