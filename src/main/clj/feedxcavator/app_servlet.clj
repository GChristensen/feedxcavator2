(ns feedxcavator.app_servlet
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use [appengine-magic.servlet :only [make-servlet-service-method]])
  (:use feedxcavator.app))

(defn -service [this request response]
  ((make-servlet-service-method feedxcavator-app) this request response))
