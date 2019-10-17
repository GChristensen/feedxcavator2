(ns feedxcavator.log
  (:require [dommy.core :refer-macros [sel sel1]]
            [dommy.core :as dommy]
            [crate.core :as crate]
            [goog.events :as events]
            [goog.dom :as dom]
            [feedxcavator.ajax :as ajax]
            )
  (:import [goog.ui Component Toolbar ToolbarButton ToolbarSeparator]))

(def oldest-log-entry (atom nil))

(defn print-log-entry [log-output entry]
  (dommy/append! log-output
    (crate/html [:div.log-entry {:id (str "log-" (:uuid entry))}
                 [:div.log-top-bar
                  [:div.log-timestamp
                    [:img.log-icon {:src (cond (= (:level entry) "error") "/images/icons/error.svg"
                                               (= (:level entry) "warning") "/images/icons/warning.svg"
                                               :else "/images/icons/info.svg")}]
                    (.toString (js/Date. (:timestamp entry)))
                    (when (:source entry)
                      [:span.log-source (str "\uD83D\uDDB6 "(:source entry))])]
                  [:div.log-top-bar-spacer]
                  [:div.log-entry-buttons
                   [:div.log-entry-unwrapper "[wrap]"]
                   [:div.log-entry-expander {:title "Expand"} "[...]"]]]
                 [:div.log-message [:pre (:message entry)]]]))

  (dommy/listen! (sel1 (str "#log-" (:uuid entry) " .log-top-bar")) :click
                 (fn [e]
                  (dommy/toggle-class! (sel1 (.-parentNode (.-parentNode (.-target e))) :.log-message) "expanded")))

  (dommy/listen! (sel1 (str "#log-" (:uuid entry) " .log-entry-unwrapper")) :click
                 (fn [e]
                   (.stopPropagation e)
                   (let [button (.-target e)]
                     (dommy/toggle-class! (sel1 (str "#log-" (:uuid entry) " .log-message pre")) "pre-wrapped")
                     (if (= (.-textContent button) "[wrap]")
                       (set! (.-textContent button) "[unwrap]")
                       (set! (.-textContent button) "[wrap]")
                     )))))

(defn update-log []
  (let [log-output (sel1 :#log-output)]
    (dommy/replace-contents! log-output
                             (crate/html [:div#log-loader-container
                                           [:img#log-loading-indicator {:src "/images/loading.svg"}]]))
    (ajax/get-edn "/front/get-log-entries" {:n 20}
                  (fn [entries]
                    (reset! oldest-log-entry (:number (last entries)))
                      (dommy/clear! log-output)
                      (set! (.-scrollTop log-output) 0)
                      (doseq [entry entries]
                        (print-log-entry log-output entry))))))

(defn construct-log-tab []
  (dommy/append! (sel1 :#tab-content)
                 (crate/html
                   [:div#log-panel.tab-panel {:style "display: none"}
                    [:div#log-toolbar.goog-toolbar
                     [:div#update-log-button.goog-toolbar-button "Refresh"]
                     [:hr]
                     [:div#clear-log-button.goog-toolbar-button "Clear"]]
                    [:div#log-output-container
                      [:div#log-output-wrapper
                        [:div#log-output]]]]))

  (let [log-toolbar (Toolbar.)]
    (.decorate log-toolbar (sel1 :div#log-toolbar))

    (events/listen (.getChild log-toolbar "update-log-button") (.-ACTION (.-EventType Component))
                   update-log)
    (events/listen (.getChild log-toolbar "clear-log-button") (.-ACTION (.-EventType Component))
                   (fn []
                     (when (js/confirm "The operation can not be undone.")
                       (ajax/get-text "/front/clear-log" update-log)))))

  (.on (js/$ "#log-panel") "click" ".log-entry-expander"
       (fn [e]
         (.stopPropagation e)
         (dommy/toggle-class! (sel1 (.-parentNode (.-parentNode (.-parentNode (.-target e)))) :.log-message) "expanded")))

  (let [log-output (sel1 :#log-output)]
    (dommy/listen! log-output :scroll
                   (fn [e]
                     (when (>= (+ (.-scrollTop log-output) (.-clientHeight log-output)) (.-scrollHeight log-output))
                       (when (and @oldest-log-entry (> (dec @oldest-log-entry) 0))
                         (ajax/get-edn "/front/get-log-entries" {:n 10 :from (dec @oldest-log-entry)}
                                       (fn [entries]
                                         (reset! oldest-log-entry (:number (last entries)))
                                         (doseq [entry entries]
                                           (print-log-entry log-output entry)))))))))

  (update-log))

(defn show-log-tab []
  (when (not (sel1 :#log-panel))
    (construct-log-tab))
  (update-log)
  (.hide (js/$ "#tab-content > div"))
  (.show (js/$ "#log-panel")))