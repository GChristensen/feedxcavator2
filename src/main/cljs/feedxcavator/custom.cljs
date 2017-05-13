;; Feedxcavator (a HTML to RSS converter)
;; (C) 2011 g/christensen (gchristnsn@gmail.com)

(ns feedxcavator.custom
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
  (.getValue (.getSession js/editor)))

(defn do-save-callback [e]
  (let [xhr (.-target e)
        response (.getResponseText xhr)]
    (show-output response)))

(defn do-retreive-callback [e]
  (let [xhr (.-target e)
        response (.getResponseText xhr)]
    (.setValue (.getSession js/editor) response)))

(defn save-btn-handler [e]
  (let [data (collect-data)]
    (when data
      (show-loading true)
      (post-data "/save-custom" data do-save-callback))))

(defn ^:export main []
  (def save-btn (goog.ui.decorate (dom/getElement "save")))

  (goog.events/listen save-btn
                      goog.ui.Component.EventType/ACTION
                      save-btn-handler)

  (post-data "/retreive-custom" "current" do-retreive-callback))