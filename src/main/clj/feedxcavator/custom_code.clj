(ns feedxcavator.custom-code
  (:require [clojure.string :as str]
            [feedxcavator.api :as api]
            [feedxcavator.db :as db]
            [clojure.data.json :as json]
            [clojure.tools.macro :as macro]
            [clj-time.core :as tm]
            [clj-time.format :as fmt])
  (:use [feedxcavator.excavation :only [make-rss-feed]]
        [feedxcavator.custom :only [$cleanup-tasks deftask schedule schedule-periodically defextractor defbackground]]
        net.cgrand.enlive-html)
  (:import [feedxcavator CFSolver]))