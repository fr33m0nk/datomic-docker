(require '[datomic.api :as d])
(import '[clojure.lang ExceptionInfo])

(def uri (format "datomic:sql://%s?%s?user=%s&password=%s"
                 (System/getenv "DATOMIC_DB_NAME")
                 (System/getenv "SQL_JDBC_URL")
                 (System/getenv "SQL_USER")
                 (System/getenv "SQL_PASSWORD")))

(defn database-migrator
  [uri]
  (println "The URI is " uri)
  (try
    (let [db-created? (d/create-database uri)]
      (if db-created?
        (do
          (println "Database successfully created in Datomic")
          :db-created)
        (do
          (printf "Database already exists in Datomic.\nNothing more to do\n")
          :db-exists)))
    (catch Throwable t
      (println "Failed to create database in datomic. Reason:")
      (if-not (instance? ExceptionInfo t)
        (println (Throwable->map t)
        (do
          (println (ex-message t))
          (println (ex-data t)))))
      :error)))

(defn create-db-in-datomic
  ([uri retries]
   (create-db-in-datomic uri retries 0))
  ([uri retries counter]
   (if (> counter retries)
     (do
       (println "Retries exhausted!!")
       (System/exit 1))
     (let [result (database-migrator uri)]
          (if (#{:db-created :db-exists} result)
            (System/exit 0)
            (let [delay-ms 5000]
              (println "Retrying in " (/ delay-ms 1000) " seconds")
              (Thread/sleep delay-ms)
              (recur uri retries (inc counter))))))))

(->> (System/getenv "RETRIES") parse-long (create-db-in-datomic uri))
