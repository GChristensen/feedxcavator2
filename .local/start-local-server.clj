;; execute in REPL:
(load-file "src/main/clj/feedxcavator/app.clj")
(eval '(do
  (in-ns 'feedxcavator.app)
  (appengine-magic.core/start feedxcavator-app)))
;(appengine-magic.feedxcavator.app/stop)