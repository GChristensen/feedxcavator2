(ns feedxcavator.code
  (:require [dommy.core :refer-macros [sel sel1]]
            [dommy.core :as dommy]
            [crate.core :as crate]
            [clojure.string :as str]
            [goog.events :as events]
            [feedxcavator.ajax :as ajax]
            )
  (:import [goog.ui Component Toolbar ToolbarButton ToolbarSeparator]))

(def refreshing-treeview (atom false))

(defn code-context-menu [node]
  (let [node (.-original node)
        id (.-id node)]
    (when id
      (cond (= (.-type node)"task")
        (clj->js {
                  :run {:label "Run"
                         :action (fn []
                                   (ajax/get-text (str "/backend/run/" id) (fn [])))}
                  :feeds {:label "List feeds"
                          :action (fn []
                                    (ajax/get-text "/front/list-task-feeds" {:task id}
                                                   (fn [r]
                                                     (let [output-editor (.edit js/ace (str "tasks-output-editor"))
                                                           output (if (map? r)
                                                                    (ajax/extract-server-error (:response r))
                                                                    r)]
                                                       (.setValue output-editor output 1)))))}
                  })))))

(defn save-code [type]
  (let [editor (.edit js/ace (str type "-editor"))]
    (set! (.-src (sel1 (str "#" type "-processing-indicator"))) "/images/loading.svg")
    (ajax/post-multipart "/front/save-code"
                         {:code (.getValue (.getSession editor))
                          :type type}
                         (fn [r]
                           (set! (.-src (sel1 (str "#" type "-processing-indicator"))) "")
                           (let [output-editor (.edit js/ace (str type "-output-editor"))
                                 output (if (map? r)
                                          (ajax/extract-server-error (:response r))
                                          r)]
                             (.setValue output-editor output 1)
                             (when (and (not (map? r)) (not= type "scratch"))
                               (js/setTimeout #(.setValue output-editor "" 1) 2000)))))))

(defn on-def-selected [prefix]
  (fn [e data]
    (when (not @refreshing-treeview)
      (let [jstree (.jstree (js/$ (str "#" prefix "-treeview")) true)
            editor (.edit js/ace (str prefix "-editor"))
            node-id (.-id (.-node data))
            original (.find (.-data (.-core (.-settings jstree))) (fn [n] (= (.-id n) node-id)))
            line (.-line original)]
        (.focus editor)
        (.moveTo (.-selection editor) line 0)
        (.scrollToLine editor line)))))

(defn assign-def-icon [type]
  (cond (= type "task") "/images/icons/task.svg"
        (= type "schedule") "/images/icons/schedule.svg"
        (= type "schedule-periodically") "/images/icons/periodic-schedule.svg"
        (= type "extractor") "/images/icons/extractor.svg"
        (= type "background") "/images/icons/background.svg"
        (= type "handler") "/images/icons/handler.svg"
        :else "/images/icons/function.svg"))

(defn update-definitions [prefix]
  (let [def-rx #"^\s*\(def([a-z0-9-]+)\*?\s+(?:\^:auth\s+)?([A-Za-z0-9-_*><$#@!~\+?]+)"
        schedule-rx #"^\s*\((schedule(?:-periodically)?)\s+([A-Za-z0-9-_*><$#@!~\+?]+)\s+(\d+)\s*(\d+)?"
        editor (.edit js/ace (str prefix "-editor"))
        jstree (.jstree (js/$ (str "#" prefix "-treeview")) true)
        defs (->> (str/split (.getValue (.getSession editor)) #"\n")
                  (map-indexed #(vector %1 %2))
                  (map #(conj (or (re-find def-rx (second %))
                                  (re-find schedule-rx (second %)))
                              (first %)))
                  (filter #(> (count %) 2))
                  (map #(hash-map :text (cond (= (nth % 1) "schedule")
                                              (str (nth % 2) " ("(nth % 3) ":" (nth % 4) ")")
                                              (= (nth % 1) "schedule-periodically")
                                              (str (nth % 2) " (every " (nth % 3) "hr)")
                                              :else (nth % 2))
                                  :parent "#"
                                  :type (nth % 1)
                                  :icon (assign-def-icon (nth % 1))
                                  :id (cond (= (nth % 1) "schedule") (str "schedule-" (first %))
                                            (= (nth % 1) "schedule-periodically") (str "schedule-periodically-" (first %))
                                            :else (nth % 2))
                                  :line (last %)))
                  (sort-by #(str/lower-case (:text %)))
                  (clj->js))
        nodes (.-data (.-core (.-settings jstree)))
        existing-defs (when nodes (set (map #(.-id %) nodes)))
        new-defs (set (map #(.-id %) defs))]

    (if (= existing-defs new-defs)
      (let [def-map (into {} (map #(vector (.-id %) %) (.-data (.-core (.-settings jstree)))))
            new-def-map (into {} (map #(vector (.-id %) %) defs))]
        (doseq [d existing-defs]
            (set! (.-line (def-map d)) (.-line (new-def-map d)))))
      (do
        (reset! refreshing-treeview true)
        (set! (.-data (.-core (.-settings jstree))) defs)
        (.refresh jstree true)

        (.off (js/$ (str "#" prefix "-treeview")) "refresh.jstree")
        (.on (js/$ (str "#" prefix "-treeview")) "refresh.jstree"
             (fn []
               (reset! refreshing-treeview false)))))))

(defn load-code [type]
  (ajax/get-text "/front/get-code" {:type type}
                 (fn [code]
                   (let [editor (.edit js/ace (str type "-editor"))]
                     (.setValue editor code 1)
                     (.setUndoManager (.getSession editor) (new js/ace.UndoManager))
                     (update-definitions type)))))

(defn construct-code-tab [prefix]
  (dommy/append! (sel1 :#tab-content)
                 (crate/html [:div.tab-panel {:id (str prefix "-panel")}
                              [:div.treeview-container
                               [:div.treeview-toolbar.goog-toolbar {:id (str prefix "-toolbar")}
                                [:div.goog-toolbar-button {:id (str "new-task-button")
                                                           :style "visibility: hidden"} "New"]]
                               #_(cond (= prefix "tasks")
                                     [:div.treeview-toolbar.goog-toolbar {:id (str prefix "-toolbar")}
                                      [:div.goog-toolbar-button {:id (str "new-task-button")} "New task"]
                                      [:hr]
                                      [:div.goog-toolbar-button {:id (str "new-schedule-button")} "New schedule"]]
                                     (= prefix "library")
                                     [:div.treeview-toolbar.goog-toolbar {:id (str prefix "-toolbar")}
                                      [:div.goog-toolbar-button {:id (str "new-function-button")} "New function"]]
                                     (= prefix "extractors")
                                     [:div.treeview-toolbar.goog-toolbar {:id (str prefix "-toolbar")}
                                      [:div.goog-toolbar-button {:id (str "new-extractor-button")} "New extractor"]
                                      [:hr]
                                      [:div.goog-toolbar-button {:id (str "new-background-button")} "New background"]]
                                     (= prefix "handlers")
                                     [:div.treeview-toolbar.goog-toolbar {:id (str prefix "-toolbar")}
                                      [:div.goog-toolbar-button {:id (str "new-handler-button")} "New handler"]]
                                     )
                               [:div.treeview-wrapper
                                 [:div.treeview-scroll-wrapper
                                  [:div.treeview {:id (str prefix "-treeview")}]]]]
                              [:div.itemview-container
                               [:div.itemview
                                [:div.item-toolbar.goog-toolbar {:id (str prefix "-code-toolbar")}
                                 [:div.goog-toolbar-button {:id (str "save-" prefix "-button")}
                                  (if (= prefix "scratch") "Run" "Save")]]
                                [:div.main-editor-wrapper [:div.main-editor {:id (str prefix "-editor")}]]
                                [:div.status-bar
                                 [:div.output-label "Output:"]
                                 [:div.spacer]
                                 [:div.processing-indicator [:img {:id (str prefix "-processing-indicator")}]]
                                 [:div.spacer]]]
                               [:div.output-editor-wrapper [:div.output-editor {:id (str prefix "-output-editor")}]]]
                              ]))

  (.jstree (js/$ (str "#" prefix "-treeview"))
           (clj->js {
                     :plugins ["contextmenu" "wholerow" "state"]
                     :core {
                            :animation 0
                            :multiple false
                            :themes {
                                     :name "default"
                                     :dots false
                                     :icons true
                                     }
                            }
                     :contextmenu {
                                   :show_at_node false
                                   :items code-context-menu
                                   }
                     :state {:key (str prefix "-tree")}
                     }))

  (let [treeview-toolbar (Toolbar.)
        code-toolbar (Toolbar.)
        ;jstree (.jstree (js/$ (str "#" prefix "-treeview")) true)
        editor (.edit js/ace (str prefix "-editor"))
        editor-session (.getSession editor)
        output-editor (.edit js/ace (str prefix "-output-editor"))
        timeout (atom nil)]

    (.decorate treeview-toolbar (sel1 (str "#" prefix "-toolbar")))
    (.decorate code-toolbar (sel1 (str "#" prefix "-code-toolbar")))

    (.setMode editor-session "ace/mode/clojure")
    (.setTheme editor "ace/theme/monokai")
    (.setShowPrintMargin editor false)
    (.setUseSoftTabs editor-session true)
    (.setTabSize editor-session 2)

    ;(.setReadOnly editor true)

    ;(.setMode (.getSession output-editor) "ace/mode/xml")
    (.setTheme output-editor "ace/theme/monokai")
    (.setShowPrintMargin output-editor false)
    (.setReadOnly output-editor true)

    #_(events/listen (.getChild treeview-toolbar "new-feed-button") (.-ACTION (.-EventType Component))
                   (fn [e]  (ajax/get-text "/front/create-new-feed"
                                           (fn [uuid]
                                             (load-treeview uuid)))))
    (dommy/listen! js/document :keydown
                   (fn [e]
                     (when (and (= (str/lower-case (.-key e)) "s")
                                (or (.-ctrlKey e) (.-metaKey e)))
                       (.preventDefault e))))

    (.addCommand (.-commands editor)
                 (clj->js {
                           :name "Save"
                           :exec (fn [] (save-code prefix))
                           :bindKey {:mac "cmd-s" :win "ctrl-s"}
                           }))

    (events/listen (.getChild code-toolbar (str "save-" prefix "-button")) (.-ACTION (.-EventType Component))
                   (fn [e]
                     (save-code prefix)))

    (.on (js/$ (str "#" prefix "-treeview")) "select_node.jstree" (on-def-selected prefix))

    (.on editor "change" (fn [e]
                           (js/clearTimeout @timeout)
                           (reset! timeout (js/setTimeout
                                             (fn []
                                               ;(when (and (.-curOp editor) (.-name (.-command (.-curOp editor))))
                                                 (update-definitions prefix)
                                                 );)
                                             3000))))
    )

  (load-code prefix))

(defn show-code-tab [prefix]
  (when (not (sel1 (str "#" prefix "-panel")))
    (construct-code-tab prefix))
  (.hide (js/$ "#tab-content > div"))
  (.show (js/$ (str "#" prefix "-panel"))))