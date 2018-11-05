(ns clojpg.handler
  (:import org.postgresql.util.PGobject)
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :refer :all]
            [clojure.java.jdbc :as sql]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [clojure.walk :as walk]
            [cheshire.core :as cheshire :refer :all]
            ))

(defn value-to-json-pgobject [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (cheshire/generate-string value))))

(extend-protocol sql/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value] (value-to-json-pgobject value)))
(extend-protocol sql/IResultSetReadColumn
  org.postgresql.util.PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type (.getType pgobj)
          value (.getValue pgobj)]
      (if (#{"jsonb" "json"} type)
        (cheshire/parse-string value true)
        value))))
(def db-spec
  (let [config (clojure.edn/read-string (slurp "resources/config.edn"))
        spec (get-in config [:postgres])]
    spec))


(defn table-exists? [table]
  (-> (sql/query db-spec
                 ["select count(*) from pg_tables where tablename = ?" table])
      first :count pos?))


(def create-table-sql
  (sql/create-table-ddl :event
                        [:id      "varchar(256)" :primary :key]
                        [:dump :json]))

(try
  (if-not (table-exists? "event")
    (sql/execute! db-spec [create-table-sql]))
  (catch Exception e (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) e ) :append true)))


(defn uuid [] (str (java.util.UUID/randomUUID)))


(defn get-all-events []
  (try
    (response (sql/query db-spec
                         (let [results ["select * from event"]]
                           (into [] results))))
    (catch Exception e (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) e ) :append true))))

(defn get-event [id]
  (try
    (let [results (sql/query db-spec ["select * from event where id = ?" id])]
      (assert (= (count results) 1))
      (response (first results)))
    (catch Exception e (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) e ) :append true))))

(defn create-new-event [doc]
  (let [e (walk/keywordize-keys doc)
        id (uuid)]
      (try
        (sql/execute! db-spec ["insert into event(id,dump) values(?,?);" id e])
        (catch Exception exc (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) exc ) :append true)))
    (get-event id)))

(defn update-event [id doc]
  (let [e (assoc doc "id" id)]
    (try
      (sql/update! db-spec :event e ["id=?" id] )
      (catch Exception exc (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) exc ) :append true)))
    (get-event id)))


(defn delete-event [id]
  (try
    (sql/delete! db-spec :event ["id=?" id])
    (catch Exception exc (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) exc ) :append true)))
  {:status 204})


(defroutes app-routes
           (context "/event" [] (defroutes events-routes
                                           (GET  "/" [] (get-all-events))
                                           (POST "/" {body :body} (create-new-event body))
                                           (context "/:id" [id] (defroutes event-routes
                                                                           (GET    "/" [] (get-event id))
                                                                           (PUT    "/" {body :body} (update-event id body))
                                                                           (DELETE "/" [] (delete-event id))))))
           (route/not-found "Not Found"))


(def app
  (-> (handler/api app-routes)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)))


