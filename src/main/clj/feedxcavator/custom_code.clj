(ns feedxcavator.custom-code
  (:require [clojure.string :as str]
            [feedxcavator.api :as api]
            [clojure.data.json :as json]
            [clojure.tools.macro :as macro]
            [clj-time.core :as tm]
            [clj-time.format :as fmt])
  (:use [feedxcavator.excavation :only [apply-selectors make-rss-feed]]
        [feedxcavator.custom :only [$cleanup-tasks deftask schedule defextractor defbackground]]
        net.cgrand.enlive-html))