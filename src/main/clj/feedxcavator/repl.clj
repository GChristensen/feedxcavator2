(in-ns 'feedxcavator.core)
(ae/def-appengine-app feedxcavator-app #'feedxcavator-app-handler
                      :war-root "d:/sandbox/clojure/feedxcavator/war")
(appengine-magic.core/start feedxcavator-app)
;(appengine-magic.core/stop)