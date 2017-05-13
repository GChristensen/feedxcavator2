(ns feedxcavator.app_context
  (:gen-class :implements [javax.servlet.ServletContextListener])
  (:require [feedxcavator.custom :as custom])
  (:import [javax.servlet ServletContextEvent]))

(defn -contextInitialized [this ^ServletContextEvent contextEvent])

(defn -contextDestroyed [this ^ServletContextEvent contextEvent])
