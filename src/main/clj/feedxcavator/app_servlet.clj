(ns feedxcavator.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use feedxcavator.core)
  (:use feedxcavator.api)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]]))


(defn -service [this request response]
  ((make-servlet-service-method feedxcavator-app) this request response))
