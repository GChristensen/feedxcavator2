(ns feedxcavator.log
  (:require [feedxcavator.core :as core]
            [feedxcavator.db :as db]
            [clojure.string :as str])
  (:import [java.io PrintWriter StringWriter]))

(defn get-stack-trace [e]
  (let [sw (StringWriter.)
        pw (PrintWriter. sw)]
    (.printStackTrace e pw)
    (.toString sw)))

(defn write
  ([level message]
   (locking
     (let [log (db/fetch :log "main")
           log (if log log {:kind "main" :top-entry 0})
           ;_ (db/store! :log (assoc log :is-open true))
           level (if (keyword? level) (name level) level)
           entry-number (inc (:top-entry log))
           entry-uuid (core/generate-uuid)
           message (cond (nil? message) "nil"
                         (instance? Throwable message) (get-stack-trace message)
                         (string? message) message
                         :else (with-out-str (clojure.pprint/pprint message)))]
       (try
         (db/store! :log-entry {:uuid      entry-uuid
                                :number    entry-number
                                :level     level
                                :source    core/*current-logging-source*
                                :timestamp (core/timestamp)})
         (db/store! :log-message {:uuid entry-uuid :message message})
         (catch Exception e
           (.printStackTrace e)))
       (db/store! :log (assoc log :top-entry entry-number :is-open false)))))
  ([message]
   (write :info message)))

(defn read-entries
  ([n from]
   (let [log (db/fetch :log "main")
         entries (if from
                     (db/query :log-entry [(<= :number from) (> :number (- from n))])
                     (db/query :log-entry (>= :number (- (:top-entry log) n))))
         messages (when (seq entries) (db/query :log-message (:in :uuid (map #(:uuid %) entries))))]
     (->> entries
          (map #(into {} %))
          (sort #(compare (:number %2) (:number %1)))
          (map #(assoc % :message (some (fn [msg] (when (= (:uuid msg) (:uuid %)) (:message msg))) messages))))))
  ([n]
   (read-entries n nil)))

(defn clear-log []
  (let [entries (db/fetch :log-entry)
        messages (map #(db/->entity :log-message {:uuid (:uuid %)}) entries)]
    (db/delete*! entries)
    (db/delete*! messages)
    (db/store! :log {:kind "main" :top-entry 0 :is-open false})))

(defmacro with-logging-source [source & body]
  `(binding [feedxcavator.core/*current-logging-source* ~source]
     ~@body))