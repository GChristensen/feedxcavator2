(ns feedxcavator.frontend
  (:require [dommy.core :refer-macros [sel sel1]]
            [dommy.core :as dommy]
            [crate.core :as crate]
            [clojure.string :as str]
            [goog.events :as events]
            [feedxcavator.ajax :as ajax]
            [feedxcavator.feeds :refer [show-feeds-tab]]
            [feedxcavator.code :refer [show-code-tab]]
            [feedxcavator.log :refer [show-log-tab]]
            [feedxcavator.settings :refer [show-settings-tab]]
            )
  (:use-macros [crate.def-macros :only [defpartial]])
  (:import [goog.ui Component Tab TabBar]))

(enable-console-print!)


(defn set-main-content [tab-id]
  (cond (= tab-id "log-tab") (show-log-tab)
        (= tab-id "feeds-tab") (show-feeds-tab)
        (= tab-id "settings-tab") (show-settings-tab)
        (str/includes? tab-id "-code-") (show-code-tab (first (str/split tab-id #"-")))
    ))

(defn set-active-tab [tabbar]
  (let [hash (.-hash js/location)
        hash (if (not= hash "")
               (.substring hash 1)
               nil)
        tab-id (when hash (str hash "-tab"))]
    (if tab-id
      (let [tab (.getChild tabbar tab-id)]
        (.setSelectedTab tabbar tab)
        (set-main-content tab-id))
      (set-main-content "feeds-tab"))))

(defn ^:export main []
  (let [tabbar (TabBar.)]
    (.decorate tabbar (sel1 :#tabbar))
    (events/listen tabbar (.-SELECT (.-EventType Component))
                   (fn [e]
                     (let [tab-id (.getId (.-target e))]
                       (set-main-content tab-id))))
    (set-active-tab tabbar)
    (ajax/get-edn "/front/get-settings"
                  (fn [settings]
                    (set! (.-feedxcavatorSettings js/window) settings)))
  ))