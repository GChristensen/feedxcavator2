(ns feedxcavator.code
  (:require [feedxcavator.core :as core]
            [feedxcavator.db :as db]
            [feedxcavator.log :as log]
            [feedxcavator.websub :as websub]
            [clojure.string :as str]
            [feedxcavator.extraction :as extraction]
            [feedxcavator.code-api :as api])
  (:use clojure.tools.macro))

(def ^:dynamic *background-tasks* (atom {}))
(def ^:dynamic *schedules* (atom []))
(def ^:dynamic *completed-schedules* (atom []))
(def ^:dynamic *periodic-schedules* (atom []))
(def ^:dynamic *handlers* (atom {}))

(defn reset-environment []
  (reset! *background-tasks* {})
  (reset! *schedules* [])
  (reset! *periodic-schedules* [])
  (reset! *handlers* {}))

(defn compile-user-code
  ([]
   (binding [*ns* (find-ns (symbol core/user-code-ns))]
     (let [code (map (fn [type]
                       (:code (first (filter (fn [code-record] (= (:type code-record) type))
                                             (db/fetch :code)))))
                     ["library" "tasks" "extractors" "handlers"])]
       (reset-environment)
       (doseq [c code]
         (when c
          (load-string c)))
       "Successfully saved.")))
  ([tab]
   (binding [*ns* (find-ns (symbol core/user-code-ns))]
     (when-let [code (:code (db/fetch :code tab))]
       (if (= tab "scratch")
         (with-out-str (load-string code))
         (do
           (load-string code)
           (try (compile-user-code)
                (catch Throwable e))))))))

(defn task-feeds [task patterns]
  (let [patterns (if (string? patterns)
                   [patterns]
                   patterns)]
    (if patterns
      (doall (filter #(some (fn [s#] (str/includes? (:title %) s#)) patterns) (db/fetch :feed)))
      (doall (db/query :feed (= :task task))))))

(defn task-feeds* [task subtasks]
  (let [subtasks (filter #(not (nil? %)) (map #(@*background-tasks* %) subtasks))]
    (doall (apply concat (map #(task-feeds (:name %) (:args %)) subtasks)))))

(defn get-task-feeds [task-name]
  (when-let [task (@*background-tasks* task-name)]
    (if (:chain task)
      (task-feeds* task-name (:args task))
      (task-feeds task-name (:args task)))))

(defn millis-to-time [n]
  (let [m (Math/floor (/ n 60000))
        s (/ (- n (* m 60000)) 1000)]
    (str (when (> m 0) (str (int m) "m ")) s "s")))

(defn print-profiling-results [task-name feeds start-time feed-start-times feed-end-times]
  (let [now (core/timestamp)]
    (log/write :info
      (str/join "\n"
                (concat
                  [(str "Task: " task-name)
                   (str "Total execution time: " (millis-to-time (- now start-time)))]
                  (for [feed feeds]
                    (let [start (feed-start-times (:uuid feed))
                          end (feed-end-times (:uuid feed))]
                      (str " - " (:title feed) ": "
                        (if (and start end)
                          (millis-to-time (- end start))
                          "N/D")))))))))

(defn fetch-feeds-in-background [task-name feeds]
  (let [settings (db/fetch :settings "main")
        start-time (core/timestamp)
        feed-start-times (atom {})
        feed-end-times (atom {})]
    (doseq [feed feeds]
      (swap! feed-start-times assoc (:uuid feed) (core/timestamp))
      (log/with-logging-source (:extractor feed)
        (try
          (extraction/extract (with-meta feed {:background true}))
          (catch Throwable e
            (log/write :error e)
            (.printStackTrace e))))
      (swap! feed-end-times assoc (:uuid feed) (core/timestamp)))
    (when (:enable-profiling settings)
      (log/with-logging-source "profiling"
        (print-profiling-results task-name feeds start-time @feed-start-times @feed-end-times)))
    (core/web-page "text/plain" "OK")))

(defmacro defextractor [fun-name [feed] & body]
  (let [extractor-fun (gensym)]
    `(do
       (defn ~extractor-fun [~feed]
         (println ~(str "executing " (name fun-name)))
         (binding [core/*current-feed* ~feed]
           (let [headlines# (extraction/filter-headlines ~feed (do ~@body))]
             (when (seq headlines#)
               (extraction/produce-feed-output ~feed headlines#)))))
       (defn ~(symbol (str (name fun-name) "-test")) [~feed]
         (~extractor-fun ~feed))
       (def ~fun-name ~extractor-fun))))

(defn handle-background-feed [feed headlines]
  (let [output (extraction/produce-feed-output feed headlines)]
    (db/store-feed-output! (:uuid feed) output)
    (when (:realtime feed)
      (try
        (if (:partition feed)
          (let [feed-url (core/get-feed-url feed)
                parts (partition-all (:partition feed) headlines)]
            (doseq [part parts]
              (Thread/sleep 1000)
              (websub/publish-content (:uuid feed) feed-url (extraction/produce-feed-output feed part))))
          (websub/publish-content (:uuid feed) (core/get-feed-url feed) output))
        (catch Throwable e
          (log/with-logging-source "websub"
            (log/write :error e))
          (.printStackTrace e))))))

(defmacro defbackground [fun-name [feed] & body]
  (let [extractor-fun (gensym)]
    `(do
       (defn ~extractor-fun [~feed]
         (println ~(str "executing " (name fun-name) " (background)"))
         (if (:background (meta ~feed))
           (binding [core/*current-feed* ~feed]
             (let [headlines# (extraction/filter-headlines ~feed (do ~@body))]
               (when (seq headlines#)
                 (handle-background-feed ~feed headlines#))))
           (db/fetch-feed-output (:uuid ~feed))))
       (defextractor ~fun-name [~feed] ~@body)
       (def ~fun-name ~extractor-fun))))

(defn task-name [task]
  (if (string? task)
    task
    (name task)))

(defmacro deftask* [symbol-or-string & args]
  (let [computed-name (task-name symbol-or-string)
        subtasks (first args)
        subtasks (when subtasks (map name subtasks))]
    `(swap! *background-tasks* assoc ~computed-name
            {:queue-fn (fn [] (core/queue-add! "/backend/execute-task"
                                               {:task-name ~computed-name
                                                :subtasks [~@subtasks]
                                                :start-time (core/timestamp)}))
             :fetch-fn (fn [params#]
                         (let [task# (first (:subtasks params#))]
                           (fetch-feeds-in-background task# (task-feeds task# nil))
                           (if (next (:subtasks params#))
                             (core/queue-add! "/backend/execute-task"
                                              (assoc params# :subtasks (next (:subtasks params#))))
                             (let [execution-time# (- (core/timestamp) (:start-time params#))]
                               (log/with-logging-source "profiling"
                                (log/write :info
                                           (str/join "\n"
                                                     (concat
                                                       [(str "Supertask: " ~computed-name)
                                                        (str "Total execution time: " (millis-to-time execution-time#))])))))
                               )))
             :name ~computed-name
             :args ~(when subtasks `(quote ~subtasks))
             :chain true})))

(defmacro deftask [symbol-or-string & args]
  (let [computed-name (task-name symbol-or-string)
        names (first args)]
    (if (seq names)
      `(deftask* ~symbol-or-string [~@names])
      `(swap! *background-tasks* assoc ~computed-name
              {:queue-fn (fn [] (core/queue-add! "/backend/execute-task"
                                                 {:task-name ~computed-name}))
               :fetch-fn (fn [params#]
                           (fetch-feeds-in-background ~computed-name
                                                      (task-feeds ~computed-name nil)))
               :name ~computed-name
               }))))

(defmacro schedule [task hours mins]
  `(when (not core/*worker-instance*)
     (swap! *schedules* conj {:task ~(task-name task) :hours ~hours :mins ~mins})))

(defmacro schedule-periodically [task hours]
  `(when (not core/*worker-instance*)
     (swap! *periodic-schedules* conj {:task ~(task-name task) :hours ~hours })))

(defn reset-completed-schedules []
  (reset! *completed-schedules* []))

(defn in-prev-5min-range [inst t]
  (and (>= inst (- t 5)) (<= inst t)))

(defn check-schedules []
  (let [date (java.util.Date.)]
    (doseq [s @*schedules*]
      (when (and (not (some #(= % s) @*completed-schedules*))
                 (== (:hours s) (.getHours date))
                 (in-prev-5min-range (:mins s) (.getMinutes date)))
        (swap! *completed-schedules* conj s)
        ((:queue-fn (@*background-tasks* (:task s))))))
    (doseq [s @*periodic-schedules*]
      (when (and (== (mod (.getHours date) (:hours s)) 0) (== (.getMinutes date) 0))
        ((:queue-fn (@*background-tasks* (:task s)))))))
    (core/web-page "text/plain" "OK"))

(defn queue-task [task]
  (if-let [queue (:queue-fn (@*background-tasks* task))]
    (try
      (queue)
      (core/web-page "text/plain" (str task " queued"))
      (catch Throwable e
        (.printStackTrace e)
        (core/web-page "text/plain" "ERROR")))
    (core/page-not-found)))

(defn execute-queued-task [request]
  (let [task-params (read-string (slurp (:body request)))
        fetch-fn (:fetch-fn (@*background-tasks* (:task-name task-params)))]
    (try
      (fetch-fn task-params)
      (catch Throwable e
        (.printStackTrace e)
        (log/with-logging-source task-params
          (log/write :error e))))
    (core/web-page "text/plain" "OK")))

(defmacro defhandler [& params]
  (let [handler-name (first params)
        authorize (:auth (meta handler-name))
        computed-name (name handler-name)
        args (second params)
        body (drop 2 params)]
    (if (= args 'request)
      `(swap! *handlers* assoc ~computed-name {:handler (fn [~'request] ~@body) :request true :auth ~authorize})
      `(swap! *handlers* assoc ~computed-name {:handler (fn [~@args] ~@body) :params '~args :auth ~authorize}))))

(defn execute-handler [request]
  (let [handler-name (:handler (:params request))
        handler (@*handlers* handler-name)
        execute (fn []
                  (if (:request handler)
                    ((:handler handler) request)
                    (let [kw-args (map #(-> % name keyword) (:params handler))
                          params (map #((:params request) %) kw-args)]
                      (apply (:handler handler) params))))]
    (if handler
      (log/with-logging-source handler-name
        (if (:auth handler)
          (core/authorized request (execute))
          (execute)))
      (core/page-not-found))))