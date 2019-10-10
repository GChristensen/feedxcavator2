(ns feedxcavator.code-user
  (:require [feedxcavator.code-api :as api]
            [feedxcavator.websub :as websub]
            [feedxcavator.log :as log]
            [feedxcavator.db :as db]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.tools.macro :as macro]
            [clj-time.core :as time]
            [clj-time.format :as time-fmt]
            [net.cgrand.enlive-html :as enlive])
  (:use [feedxcavator.code :only [deftask deftask* schedule schedule-periodically
                                  defextractor defbackground defhandler]]))

(def ?* enlive/select)
(defn ?1 [node-or-nodes selector] (first (enlive/select node-or-nodes selector)))
(defn ?1a [node-or-nodes selector] (:attrs (first (enlive/select node-or-nodes selector))))
(defn ?1c [node-or-nodes selector] (:content (first (enlive/select node-or-nodes selector))))
(defn <t [node-or-nodes] (str/trim (enlive/text node-or-nodes)))
(def <* api/html-render)