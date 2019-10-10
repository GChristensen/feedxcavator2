(ns feedxcavator.settings
  (:require [dommy.core :refer-macros [sel sel1]]
            [dommy.core :as dommy]
            [crate.core :as crate]
            [feedxcavator.ajax :as ajax]
            ))

(def main-settings (atom {}))

(defn update-customization-settings [e]
  (swap! main-settings assoc :subscription-url (dommy/value (sel1 :#subscription-url)))
  (swap! main-settings assoc :user-email (dommy/value (sel1 :#user-email)))
  (set! (.-feedxcavatorSettings js/window) @main-settings)
  (ajax/post-multipart "/front/save-settings" {:settings @main-settings} (fn [])))

(defn construct-settings-tab []
  (dommy/append! (sel1 :#tab-content)
                 (crate/html
                   [:div#settings-panel {:style "display: none"}
                    [:div#settings-wrapper
                     [:fieldset.settings
                      [:legend "Customization"]
                      [:table.customization-settings {:cellpadding "3px" :cellspacing "0" :style "width: 100%"}
                        [:tr
                         [:td
                           "Subscription URL: "]
                         [:td [:input#subscription-url {:type "text" :style "width: 100%"}]]]
                       [:tr
                        [:td
                         "User e-mail: "]
                        [:td [:input#user-email {:type "text" :style "width: 100%"}]]]]]
                      [:fieldset.settings
                       [:legend "Authentication"]
                       [:p
                        "x-feedxcavator-auth: " [:span#auth-token] "  "
                        [:button#generate-auth-key-button "Generate"]]]
                      [:fieldset.settings
                       [:legend "Database"]
                       [:p
                        ;[:form
                        [:span "Restore database: "]
                        [:input#restore-db-file {:type "file" :name "edn"}]
                        [:button#restore-db-button "Load"]
                        ;]
                        ]
                       [:p
                        [:a.decorated-link {:href "/front/backup-database"} "[Backup database]"]]]
                        ;[:a.decorated-link {:href "#"} "[Backup database]"]]]
                      [:fieldset.settings
                       [:legend "Logging"]
                       [:p
                        [:input#enable-profiling {:type "checkbox"}]
                        [:label {:for "enable-profiling"} "Enable profiling"] ]]
                     [:div#app-version [:a {:href "https://gchristensen.github.io/feedxcavator2"
                                            :target "_blank"}
                                            "Feedxcavator"]
                      [:span#app-version-number]]]]
                   ))

  (dommy/listen! (sel1 :#restore-db-button) :click
                 (fn [e]
                   (let [^js/File file (-> (sel1 :#restore-db-file) .-files (aget 0))]
                     (ajax/post-multipart "/front/restore-database" {:edn file} (fn [])))))

  (dommy/listen! (sel1 :#generate-auth-key-button) :click
                 (fn [e]
                   (ajax/get-text "/front/gen-auth-token"
                                  (fn [token]
                                    (dommy/set-text! (sel1 :#auth-token) token)))))

  (ajax/get-text "/front/get-auth-token"
                 (fn [token]
                   (dommy/set-text! (sel1 :#auth-token) token)))

  (ajax/get-edn "/front/get-settings"
                 (fn [settings]
                   (reset! main-settings settings)
                   (set! (.-feedxcavatorSettings js/window) settings)
                   (set! (.-value (sel1 :#subscription-url)) (:subscription-url settings))
                   (set! (.-value (sel1 :#user-email)) (:user-email settings))
                   (set! (.-checked (sel1 :#enable-profiling)) (:enable-profiling settings))
                   (set! (.-textContent (sel1 :#app-version-number)) (str " version: " (:version settings)))
                   ))

  (dommy/listen! (sel1 :#enable-profiling) :change
                 (fn [e]
                   (swap! main-settings assoc :enable-profiling (.-checked (sel1 :#enable-profiling)))
                   (ajax/post-multipart "/front/save-settings" {:settings @main-settings} (fn []))))

  (dommy/listen! (sel1 :#subscription-url) :blur update-customization-settings)
  (dommy/listen! (sel1 :#user-email) :blur update-customization-settings)

  )

(defn show-settings-tab []
  (when (not (sel1 :#settings-panel))
    (construct-settings-tab))
  (.hide (js/$ "#tab-content > div"))
  (.show (js/$ "#settings-panel")))