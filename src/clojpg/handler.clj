(ns clojpg.handler
  (:require [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :refer :all]
            [clojure.java.jdbc :as sql]
            [compojure.route :as route]
            [compojure.core :refer :all]
            ))

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
                        [:message "varchar"]
                        [:rule :varchar]
                        ))

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
  (let [id (uuid)]
    (let [a-event (assoc doc "id" id)]
      (try
        (sql/insert! db-spec :event a-event)
        (catch Exception e (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) e ) :append true))))
    (get-event id)))


(defn update-event [id doc]
  (let [a-event (assoc doc "id" id)]
    (try
      (sql/update! db-spec :event a-event ["id=?" id] )
      (catch Exception e (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) e ) :append true)))
    (get-event id)))


(defn delete-event [id]
  (try
    (sql/delete! db-spec :event ["id=?" id])
    (catch Exception e (spit "error.log" (format "%s - %s\n" (str(java.time.LocalDateTime/now)) e ) :append true)))
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


