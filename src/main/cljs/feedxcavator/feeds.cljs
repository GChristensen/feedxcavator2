(ns feedxcavator.feeds
  (:require [dommy.core :refer-macros [sel sel1]]
            [dommy.core :as dommy]
            [crate.core :as crate]
            [clojure.string :as str]
            [goog.events :as events]
            [feedxcavator.ajax :as ajax]
            )
  (:import [goog.ui Component Toolbar ToolbarButton ToolbarSeparator]))

(def treeview-populated (atom false))
(def refreshing-treeview (atom false))

(defn feeds-to-jstree-nodes [nodes]
  (let [groups (->> nodes
                    (map #(:group %))
                    distinct
                    (filter #(not (nil? %)))
                    (map #(vector % (str/split (str/replace % "\\" "/") #"/")))
                    (sort-by #(count (second %)))
                    (map #(assoc {} :text (last (second %)) :type "group"
                                    :icon "/images/icons/group.svg" :id (first %)
                                    :level (count (second %)))))
        groups (map #(assoc % :parent (if (> (:level %) 1)
                                        (str/join "/" (butlast (str/split (:id %) #"/")))
                                        "#"))
                    groups)
        non-existent-groups (->> groups
                                 (map #(:parent %)) distinct (filter #(not= % "#"))
                                 (filter #(not (some (fn [g] (= % (:id g))) groups)))
                                 (map #(assoc {} :text (last (str/split % #"/")) :type "group"
                                                 :icon "/images/icons/group.svg" :id % :parent "#"
                                                 :level (inc (count (filter (fn [c] (= c "/")) %))))))
        feeds (for [n nodes]
                {:text (:title n)
                 :parent (if (:group n) (:group n) "#")
                 :type "feed"
                 :icon "/images/icons/feed.svg"
                 :id (:uuid n)
                 :uuid (:uuid n)
                 "a_attr" {"data-uuid" (:uuid n)}
                 })]
    (clj->js (sort-by #(str/lower-case (:text %)) (concat non-existent-groups groups feeds)))))

(def selected-feed-uuid (atom nil))

(defn edit-feed-definition [uuid]
  (reset! selected-feed-uuid uuid)
  (ajax/get-text "/front/feed-definition" {:uuid uuid}
                 (fn [yaml]
                   (let [editor (.edit js/ace "feeddef-editor")
                         output-editor (.edit js/ace "feed-output-editor")]
                     (.setValue output-editor "" 1)
                     (if (map? yaml)
                       (.setValue editor "" 1)
                       (.setValue editor yaml 1))
                     (.setUndoManager (.getSession editor) (new js/ace.UndoManager))))))

(defn on-feed-selected [e data]
  (when (not @refreshing-treeview)
    (let [uuid (.-uuid (.-original (.-node data)))]
      (edit-feed-definition uuid))))

(defn load-treeview
  ([edit-selected]
   (ajax/get-edn "/front/list-feeds"
                 (fn [feeds]
                   (let [jstree (.jstree (js/$ "#feed-treeview") true)
                         nodes (feeds-to-jstree-nodes feeds)
                         state (.parse js/JSON (.getItem js/localStorage "feed-tree"))
                         state (when state (.-state state))]

                     (reset! refreshing-treeview @treeview-populated)
                     (.off (js/$ "#feed-treeview") "refresh.jstree")
                     (.on (js/$ "#feed-treeview") "refresh.jstree"
                          (fn []
                            (reset! refreshing-treeview false)
                            (reset! treeview-populated true)
                            (when (string? edit-selected)
                              (try
                                (.deselect-all jstree)
                                (.select-node jstree (.get-node jstree (str edit-selected)))
                                (catch js/Error e (.error js/console e))))))

                     (set! (.-data (.-core (.-settings jstree))) nodes)
                     (.refresh jstree true (fn [] state))))))
  ([] (load-treeview true)))

(defn save-feed-definition [uuid]
  (let [editor (.edit js/ace "feeddef-editor")]
    (set! (.-src (sel1 :#feed-processing-indicator)) "/images/loading.svg")
    (ajax/post-multipart "/front/feed-definition"
                         {:yaml (.getValue (.getSession editor))
                          :uuid uuid}
                         (fn [r]
                           (set! (.-src (sel1 :#feed-processing-indicator)) "")
                           (.setUndoManager (.getSession editor) (new js/ace.UndoManager))
                           (let [output-editor (.edit js/ace "feed-output-editor")]
                             (if (map? r)
                               (.setValue output-editor (:response r) 1)
                               (do (.setValue output-editor r 1)
                                   (js/setTimeout #(.setValue output-editor "" 1) 2000))))
                             (load-treeview false)))))

(defn test-current-feed-definition []
  (let [editor (.edit js/ace "feeddef-editor")]
    (set! (.-src (sel1 :#feed-processing-indicator)) "/images/loading.svg")
    (ajax/post-multipart "/front/test-feed"
                         {:yaml (.getValue (.getSession editor))
                          :uuid @selected-feed-uuid}
                         (fn [r]
                           (set! (.-src (sel1 :#feed-processing-indicator)) "")

                           (let [output (if (map? r)
                                          (ajax/extract-server-error (:response r))
                                          (if (or (str/starts-with? r "[{")
                                                  (str/starts-with? r "{\"version"))
                                            (.stringify js/JSON (.parse js/JSON r) nil 2)
                                            r))]
                           (let [editor (.edit js/ace "feed-output-editor")]
                             (.setValue editor output 1)
                             #_(.setUndoManager (.getSession editor) (new js/ace.UndoManager))))))))

(defn delete-feed [uuid]
  (when (js/confirm "Do you really want to delete the selected feed?")
    (ajax/get-text "/front/delete-feed" {:uuid uuid}
                   (fn []
                     (let [editor (.edit js/ace "feeddef-editor")]
                       (.setValue editor "" 1)
                       (.setUndoManager (.getSession editor) (new js/ace.UndoManager)))
                     (load-treeview false)))))

(defn feeds-context-menu [node]
  (when-let [uuid (.-uuid (.-original node))]
    (clj->js {
              :open {:label "Open feed URL"
                     :action (fn []
                               )
                     }
              :subscribe
                    {:label "Subscribe"
                     :action (fn []
                               )
                     }
              :delete {:label "Delete"
                       :separator_before true
                       :action #(delete-feed uuid)
                         }})))

(defn with-selected-node [f]
  (let [jstree (.jstree (js/$ "#feed-treeview") true)
        nodes (.get-selected jstree true)]
    (when (> (.-length nodes) 0)
      (when-let [uuid (.-uuid (.-original (aget nodes 0)))]
        (f uuid)))))

(defn construct-feeds-tab []
  (dommy/append! (sel1 :#tab-content)
                 (crate/html [:div#feeds-panel.tab-panel
                              [:div.treeview-container
                               [:div#feed-toolbar.treeview-toolbar.goog-toolbar
                                [:div#new-feed-button.goog-toolbar-button "New Feed"]]
                               [:div.treeview-wrapper
                                [:div.treeview-scroll-wrapper
                                  [:div#feed-treeview.treeview]]]]
                              [:div.itemview-container
                               [:div.itemview
                                [:div#feeddef-toolbar.item-toolbar.goog-toolbar
                                 [:div#save-feed-button.goog-toolbar-button "Save"]
                                 [:hr]
                                 [:div#test-feed-button.goog-toolbar-button "Test"]
                                 [:hr]
                                 [:div#open-feed-button.goog-toolbar-button "Open"]
                                 [:div#subscribe-feed-button.goog-toolbar-button "Subscribe"]
                                 [:hr]
                                 [:div#delete-feed-button.goog-toolbar-button "Delete"]]
                                [:div.main-editor-wrapper [:div#feeddef-editor.main-editor]]
                                [:div.status-bar
                                 [:div.output-label "Output:"]
                                 [:div.spacer]
                                 [:div.processing-indicator [:img#feed-processing-indicator]]
                                 [:div.spacer]]]
                               [:div.output-editor-wrapper [:div#feed-output-editor.output-editor]]]
                              ]))

  (.jstree (js/$ "#feed-treeview")
           (clj->js {
                     :plugins ["contextmenu" "wholerow" "state" "conditionalselect"]
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
                                   :items feeds-context-menu
                                   }
                     :state {:key "feed-tree"}
                     :conditionalselect (fn []
                                          (let [editor (.edit js/ace "feeddef-editor")]
                                            (if (not (.isClean (.getUndoManager (.getSession editor))))
                                              (js/confirm "Discard unsaved changes?")
                                              true)))
                     }))

  (let [treeview-toolbar (Toolbar.)
        feeddef-toolbar (Toolbar.)
        jstree (.jstree (js/$ "#feed-treeview") true)
        editor (.edit js/ace "feeddef-editor")
        editor-session (.getSession editor)
        output-editor (.edit js/ace "feed-output-editor")]

    (.decorate treeview-toolbar (sel1 :div#feed-toolbar))
    (.decorate feeddef-toolbar (sel1 :div#feeddef-toolbar))

    (.setMode editor-session "ace/mode/yaml")
    (.setTheme editor "ace/theme/monokai")
    (.setShowPrintMargin editor false)
    (.setUseSoftTabs editor-session true)
    (.setTabSize editor-session 2)

    ;(.setReadOnly editor true)

    (.setMode (.getSession output-editor) "ace/mode/xml")
    (.setTheme output-editor "ace/theme/monokai")
    (.setShowPrintMargin output-editor false)
    (.setReadOnly output-editor true)


    ;(js/SimpleBar. (sel1 :.treeview-scroll-wrapper) (clj->js {:autoHide false}))
    ;
    ;                 (.log js/console (sel1 [:#feeddef-editor :.ace_content])
    ;(js/SimpleBar. (sel1 [:#feeddef-editor :.ace_editor]) (clj->js {:autoHide false})))
    ;;(js/SimpleBar. (sel1 [:#feedef-output-editor :.ace_content]) (clj->js {:autoHide false}))

    (events/listen (.getChild treeview-toolbar "new-feed-button") (.-ACTION (.-EventType Component))
                   (fn [e]  (ajax/get-text "/front/create-new-feed"
                                           (fn [uuid]
                                             (load-treeview uuid)))))

    (events/listen (.getChild feeddef-toolbar "save-feed-button") (.-ACTION (.-EventType Component))
                   (fn [e] (with-selected-node save-feed-definition)))

    (dommy/listen! js/document :keydown
                   (fn [e]
                     (when (and (= (str/lower-case (.-key e)) "s")
                                (or (.-ctrlKey e) (.-metaKey e)))
                       (.preventDefault e))))
    
    (.addCommand (.-commands editor)
                 (clj->js {
                           :name "Save"
                           :exec (fn [] (with-selected-node save-feed-definition))
                           :bindKey {:mac "cmd-s" :win "ctrl-s"}
                           }))

    (events/listen (.getChild feeddef-toolbar "test-feed-button") (.-ACTION (.-EventType Component))
                   (fn [e]
                       (when @selected-feed-uuid
                         (test-current-feed-definition))) )

    (events/listen (.getChild feeddef-toolbar "open-feed-button") (.-ACTION (.-EventType Component))
                   (fn [e]
                     (when @selected-feed-uuid
                       (ajax/get-text "/front/feed-url" {:uuid @selected-feed-uuid}
                         (fn [url]
                           (.open js/window url "_blank"))))))

    (events/listen (.getChild feeddef-toolbar "subscribe-feed-button") (.-ACTION (.-EventType Component))
                   (fn [e]
                     (when @selected-feed-uuid
                       (ajax/get-text "/front/feed-url" {:uuid @selected-feed-uuid}
                                      (fn [url]
                                        (print (.-feedxcavatorSettings js/window))
                                        (let [subscription-url (or (:subscription-url (.-feedxcavatorSettings js/window))
                                                                   "https://feedly.com/i/subscription/feed/")
                                              subscription-url (str subscription-url (js/encodeURIComponent url))]
                                        (.open js/window subscription-url "_blank")))))))

    (events/listen (.getChild feeddef-toolbar "delete-feed-button") (.-ACTION (.-EventType Component))
                   (fn [e] (with-selected-node delete-feed)))

    (.on (js/$ "#feed-treeview") "select_node.jstree" on-feed-selected)
    )

  (load-treeview))

(defn show-feeds-tab []
  (when (not (sel1 :#feeds-panel))
    (construct-feeds-tab))
  (.hide (js/$ "#tab-content > div"))
  (.show (js/$ "#feeds-panel")))