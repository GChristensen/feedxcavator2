(ns feedxcavator.db
  (:require [appengine-magic.services.datastore :as ds]
            [clj-gcloud.storage :as gcs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [feedxcavator.core :as core])
  (:use clojure.tools.macro
        [clojure.pprint :only [pprint]])
  (:import java.nio.channels.Channels
           java.io.ByteArrayOutputStream
           java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream
           [com.google.cloud.storage Acl Acl$User Acl$Role StorageOptions]
           [com.google.cloud ServiceOptions Service]
           [com.google.auth.oauth2 ServiceAccountCredentials]))

(def ^:const NS 'feedxcavator.db)

(def converters (atom {}))

;; datastore ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:const db-record-limit 1048000)

(defn kind-from-kw [kw]
  (ns-resolve (find-ns NS) (symbol (core/kebab-to-pascal (name kw)))))

(defmacro defentity [key & fields]
  (let [kebab-name# (name key)
        pascal-name# (core/kebab-to-pascal kebab-name#)]
  `(do
     (defsymbolmacro ~(symbol (str kebab-name# "-fields")) (~@fields))
     (ds/defentity ~(symbol pascal-name#) [~@fields])
     (swap! converters assoc ~key
            (ns-resolve (find-ns NS) (symbol (str "map->" (core/kebab-to-pascal (name ~key)))))))))


(defentity :_object ^:key uuid ^:clj object)

(defentity :settings ^:key kind enable-profiling subscription-url user-email)

(defentity :feed ^:key uuid title suffix source charset output group task
           ^:clj selectors ^:clj pages ^:clj filter realtime extractor partition
           ^:clj $$extra)

(defentity :feed-definition ^:key uuid ^:clj yaml)
(defentity :feed-params ^:key uuid ^:clj params)

(defentity :image ^:key uuid url content-type timestamp)

(defentity :blob ^:key uuid data content-type compressed)

(defentity :code ^:key type ^:clj code timestamp)

(defentity :auth-token ^:key kind token)

(defentity :subscription ^:key uuid name topic callback secret timestamp)

(defentity :history ^:key uuid ^:clj items)

(defentity :sample ^:key uuid ^:clj data)

(defentity :word-filter ^:key id ^:clj words)

(defentity :log ^:key kind top-entry is-open)
(defentity :log-entry ^:key uuid number level source timestamp)
(defentity :log-message ^:key uuid ^:clj message)


(defn ->entity [kind map]
  ((@converters kind) map))

(defn fetch
  ([kind]
   (ds/query :kind (kind-from-kw kind)))
  ([kind key]
    (ds/retrieve (kind-from-kw kind) key)))

(defmacro query [kind filter]
  (let [kind-sym (gensym)]
    `(let [~kind-sym (feedxcavator.db/kind-from-kw ~kind)]
       (appengine-magic.services.datastore/query :kind ~kind-sym :filter ~filter))))

(defn store! [kind entity]
  (ds/save! (->entity kind entity)))

(defn delete!
  ([kind key]
    (when-let [entity (fetch kind key)]
      (ds/delete! entity)))
  ([entity]
    (ds/delete! entity)))

(defn delete*! [entities]
  (ds/delete! entities))

(defn fetch-object [uuid]
  (:object (fetch :_object uuid)))

(defn store-object! [uuid obj]
  (store! :_object {:uuid uuid :object obj}))

(defn delete-object! [uuid]
  (delete! :_object uuid))

(defn find-feed [& {:keys [title suffix]}]
  (let [feed (first
               (if suffix
                 (query :feed (= :suffix suffix))
                 (query :feed (= :title title))))]
    (when feed
      (assoc feed :params (:params (fetch :feed-params (:uuid feed)))))))

(defn persist-feed! [feed]
  (let [params (:params feed)
        feed (dissoc feed :params)]
    (store! :feed feed)
    (when params
      (store! :feed-params {:uuid (:uuid feed) :params params}))))

(defn extra [feed field]
  (when-let [extra-fields (:$$extra feed)]
    (extra-fields field)))

;; cloud storage ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro compiled-content [file]
  (when (not= core/blob-implementation :datastore)
    (let [s (slurp file)]
      (str s))))

;; the simplest way to call gcs from cloud tasks is to build with predefined credentials
;; https://www.baeldung.com/java-google-cloud-storage
(defn build-gcs-service ^Service []
  (let [builder (StorageOptions/newBuilder)]
    (with-open [stream (io/input-stream (.getBytes (compiled-content
                                                     ".local/credentials/gcs-auth.json") "UTF-8"))]
      (doto builder
        (.setProjectId (compiled-content ".local/credentials/project_id"))
        (.setCredentials (ServiceAccountCredentials/fromStream stream))))
    (.getService ^ServiceOptions (.build builder))))

(def cloud-storage
  (when (not= core/blob-implementation :datastore)
    (if (core/production?)
      (build-gcs-service)
      (let [project-id (slurp ".local/credentials/project_id")]
        (gcs/init
          {:project-id  project-id
           :credentials ".local/credentials/gcs-auth.json"})))))

(def default-bucket
  (when (not= core/blob-implementation :datastore)
    (if (core/production?)
      (str core/application-id ".appspot.com")
      (str (slurp ".local/credentials/project_name") ".appspot.com"))))

(defn blob-info [name metadata]
  (gcs/blob-info
    (gcs/->blob-id default-bucket name)
    metadata))

(defn create-blob
  ([blob-info]
   (gcs/create-blob cloud-storage blob-info))
  ([name metadata]
   (gcs/create-blob cloud-storage (blob-info name metadata))))


(defn fetch-text [uuid]
  (let [blob-id (gcs/->blob-id default-bucket (str "text/" uuid))
        blob (gcs/get-blob cloud-storage blob-id)]
    (when blob
      (let [content-type (.getContentType blob)]
        (with-open [from (Channels/newInputStream (gcs/read-channel blob))
                    to   (ByteArrayOutputStream. (.getSize blob))]
          (io/copy from to)
          {:uuid uuid
           :content-type content-type
           :text (String. (.toByteArray to) "UTF-8")})))))

(defn store-text! [obj]
  (let [blob-id (gcs/->blob-id default-bucket (str "text/" (:uuid obj)))
        blob-info (gcs/blob-info blob-id {:content-type (:content-type obj)})
        blob (create-blob blob-info)]
    (with-open [from (io/input-stream (.getBytes (:text obj) "UTF-8"))
                to   (Channels/newOutputStream (gcs/write-channel blob))]
      (io/copy from to))))

(defn delete-text! [uuid]
  (let [blob-id (gcs/->blob-id default-bucket (str "text/" uuid))]
    (gcs/delete-blob cloud-storage blob-id)))


(defn get-blob [uuid]
  (let [blob-id (gcs/->blob-id default-bucket (str "blob/" uuid))]
    (gcs/get-blob cloud-storage blob-id)))

(defn get-blob-url [uuid]
  (str "https://storage.cloud.google.com/" default-bucket "/blob/" uuid))

(defn fetch-blob [uuid]
  (let [blob-id (gcs/->blob-id default-bucket (str "blob/" uuid))
        blob (gcs/get-blob cloud-storage blob-id)]
    (when blob
      (let [content-type (.getContentType blob)]
        (with-open [from (Channels/newInputStream (gcs/read-channel blob))
                    to   (ByteArrayOutputStream. (.getSize blob))]
          (io/copy from to)
          {:uuid uuid
           :content-type content-type
           :bytes (.toByteArray to)})))))

(defn store-blob! [obj]
  (let [blob-id (gcs/->blob-id default-bucket (str "blob/" (:uuid obj)))
        blob-info (gcs/blob-info blob-id {:content-type (:content-type obj)})
        blob (create-blob blob-info)]
    (with-open [from (io/input-stream (:bytes obj))
                to   (Channels/newOutputStream (gcs/write-channel blob))]
      (io/copy from to))
    (when (:public obj)
      (.createAcl cloud-storage blob-id (Acl/of (Acl$User/ofAllUsers) Acl$Role/READER)))))

(defn delete-blob! [uuid]
  (let [blob-id (gcs/->blob-id default-bucket (str "blob/" uuid))]
    (gcs/delete-blob cloud-storage blob-id)))


(defn image-exists? [url]
  (first (query :image (= :url url))))

(defn get-cloud-image-url [uuid]
  (str "https://storage.googleapis.com/" default-bucket "/blob/image/" uuid))

(defn get-image-url [uuid]
  (str (core/get-app-host) "/image/" uuid))

(defn fetch-image [uuid]
  (let [;image (fetch :image uuid)
        blob (fetch-blob (str "image/" uuid))]
    blob))

(defn store-image! [uuid url content-type data]
  (store! :image {:uuid uuid :url url :content-type content-type :timestamp (core/timestamp)})
  (store-blob! {:uuid (str "image/" uuid) :content-type content-type :bytes data :public true}))

(defn delete-image! [uuid]
  (delete-blob! (str "image/" uuid))
  (delete! :image uuid))


(defn fetch-feed-output [uuid]
  (let [text (fetch-text (str "feed/" uuid))]
    {:output (:text text)
     :content-type (:content-type text)}))

(defn store-feed-output! [uuid obj]
  (store-text! {:uuid (str "feed/" uuid) :content-type (:content-type obj) :text (:output obj)}))

(defn delete-feed-output! [uuid]
  (delete-text! (str "feed/" uuid)))


;; DS blob implementation, limited to 1MB

(defn gunzip [input output & opts]
  (with-open [input (-> input io/input-stream GZIPInputStream.)]
    (apply io/copy input output opts)))

(defn gzip [input output & opts]
  (with-open [output (-> output io/output-stream GZIPOutputStream.)]
    (apply io/copy input output opts)))

(defn shrink-for-ds [text]
  (let [content-bytes (.getBytes text "utf-8")
        ;;_ (println (str "Content size: " (alength content-bytes)))
        compress? (>= (alength content-bytes) db-record-limit)
        content (if compress?
                  (with-open [byte-stream (java.io.ByteArrayOutputStream.)]
                    (gzip content-bytes byte-stream)
                    (.toByteArray byte-stream))
                  text)
        result-fn (if compress? ds/as-blob ds/as-text)]
    [(result-fn content) compress?]))

(defn unshrink-from-ds [blob compressed?]
  (if compressed?
    (with-open [byte-stream (java.io.ByteArrayOutputStream.)]
      (gunzip (.getBytes blob) byte-stream)
      (String. (.toByteArray byte-stream) "utf-8"))
    (.getValue blob)))

(defn fetch-text-ds [uuid]
 (when-let [blob (fetch :blob uuid)]
   {:uuid uuid
    :content-type (:content-type blob)
    :text (unshrink-from-ds (:data blob) (:compressed blob))}))

(defn store-text-ds! [obj]
  (let [[content compressed?] (shrink-for-ds (:text obj))]
    (store! :blob {:uuid (:uuid obj)
                   :content-type (:content-type obj)
                   :data content
                   :compressed compressed?})))

(defn delete-text-ds! [uuid]
  (delete! :blob {:uuid uuid}))


(defn fetch-blob-ds [uuid]
  (when-let [blob (fetch :blob uuid)]
    {:uuid uuid
     :content-type (:content-type blob)
     :bytes (.getBytes (:data blob))}))

(defn store-blob-ds! [obj]
  (store! :blob {:uuid (:uuid obj)
                 :content-type (:content-type obj)
                 :data (ds/as-blob (:bytes obj))}))

(defn delete-blob-ds! [uuid]
  (delete! :blob {:uuid uuid}))

(when (= core/blob-implementation :datastore)
  (eval '(do
          (def fetch-text fetch-text-ds)
          (def store-text! store-text-ds!)
          (def delete-text! delete-text-ds!)

          (def fetch-blob fetch-blob-ds)
          (def store-blob! store-blob-ds!)
          (def delete-blob! delete-blob-ds!))))