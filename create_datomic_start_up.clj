(ns create-datomic-start-up
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (java.nio.file Files Paths)
    (java.nio.file.attribute PosixFilePermission)))

(defn env-var-exists?
  [env-var]
  (and (some? (System/getenv env-var))
       (not (str/blank? (System/getenv env-var)))))

(defn assert-not-nil
  [env-var]
  (assert (env-var-exists? env-var)
          (format "%s environment variable must be supplied" env-var))
  (System/getenv env-var))

(def datomic-startup-file (assert-not-nil "BOOTSTRAP_FILE_NAME"))

(defn append-to-file
  []
  (let [new-file? (atom true)]
    (fn append-to-file! [file-name string]
      (spit file-name (str string "\n") :append (not (compare-and-set! new-file? true false))))))

(defn make-script-executable
  [script-name]
  (-> script-name
      (io/file)
      (.toURI)
      (Paths/get)
      (Files/setPosixFilePermissions #{PosixFilePermission/OWNER_READ
                                       PosixFilePermission/OWNER_WRITE
                                       PosixFilePermission/OWNER_EXECUTE
                                       PosixFilePermission/GROUP_READ
                                       PosixFilePermission/GROUP_EXECUTE
                                       PosixFilePermission/OTHERS_EXECUTE})))

(defmulti datomic-uri-for-console (fn [env-var-map] (get env-var-map "PROTOCOL")))

(defmethod datomic-uri-for-console "DDB-LOCAL"
  [_]
  (format "datomic:ddb-local://%s/%s/"
          (assert-not-nil "LOCAL_DYNAMO_DB_ENDPOINT")
          (assert-not-nil "DYNAMO_DB_TABLE")))

(defmethod datomic-uri-for-console "DDB"
  [_]
  (format "datomic:ddb://%s/%s/"
          (assert-not-nil "AWS_DYNAMO_DB_REGION")
          (assert-not-nil "DYNAMO_DB_TABLE")))

(defmethod datomic-uri-for-console "SQL"
  [_]
  (format "datomic:sql://?%s?user=%s&password=%s"
          (assert-not-nil "SQL_JDBC_URL")
          (assert-not-nil "SQL_USER")
          (assert-not-nil "SQL_PASSWORD")))

(defmethod datomic-uri-for-console :default
  [env-var-map]
  (println (format "Invalid CONSOLE PROTOCOL: %s supplied. SQL, DDB and DDB-LOCAL are the only supported values."
                   (get env-var-map "PROTOCOL")))
  (System/exit 1))

(defmulti datomic-uri (fn [env-var-map] (get env-var-map "PROTOCOL")))

(defmethod datomic-uri "DDB-LOCAL"
  [_]
  (format "datomic:ddb-local://%s/%s/%s"
          (assert-not-nil "LOCAL_DYNAMO_DB_ENDPOINT")
          (assert-not-nil "DYNAMO_DB_TABLE")
          (assert-not-nil "DATOMIC_DB_NAME")))

(defmethod datomic-uri "DDB"
  [_]
  (format "datomic:ddb://%s/%s/%s"
          (assert-not-nil "AWS_DYNAMO_DB_REGION")
          (assert-not-nil "DYNAMO_DB_TABLE")
          (assert-not-nil "DATOMIC_DB_NAME")))

(defmethod datomic-uri "SQL"
  [_]
  (format "datomic:sql://%s?%s?user=%s&password=%s"
          (assert-not-nil "DATOMIC_DB_NAME")
          (assert-not-nil "SQL_JDBC_URL")
          (assert-not-nil "SQL_USER")
          (assert-not-nil "SQL_PASSWORD")))

(defmethod datomic-uri :default
  [env-var-map]
  (println (format "Invalid DATOMIC URI PROTOCOL: %s supplied. SQL, DDB and DDB-LOCAL are the only supported values."
                   (get env-var-map "PROTOCOL")))
  (System/exit 1))

(defn common-transactor-config
  [config-vector]
  (-> config-vector
      (conj (format "host=%s" (assert-not-nil "TRANSACTOR_HOST")))
      (conj (format "port=%s" (assert-not-nil "TRANSACTOR_PORT")))
      (conj (format "alt-host=%s" (assert-not-nil "TRANSACTOR_ALT_HOST")))
      (conj (format "ping-host=%s" (assert-not-nil "TRANSACTOR_HEALTHCHECK_HOST")))
      (conj (format "ping-port=%s" (assert-not-nil "TRANSACTOR_HEALTHCHECK_PORT")))
      (conj (format "ping-concurrency=%s" (assert-not-nil "TRANSACTOR_HEALTHCHECK_CONCURRENCY")))
      (conj (format "heartbeat-interval-msec=%s" (assert-not-nil "TRANSACTOR_HEARTBEAT_INTERVAL_IN_MS")))
      (conj (format "encrypt-channel=%s" (assert-not-nil "TRANSACTOR_ENCRYPT_CHANNEL")))
      (conj (format "write-concurrency=%s" (assert-not-nil "TRANSACTOR_WRITE_CONCURRENCY")))
      (conj (format "read-concurrency=%s" (assert-not-nil "TRANSACTOR_READ_CONCURRENCY")))
      (conj (format "memory-index-threshold=%s" (assert-not-nil "MEMORY_INDEX_THRESHOLD")))
      (conj (format "memory-index-max=%s" (assert-not-nil "MEMORY_INDEX_MAX")))
      (conj (format "object-cache-max=%s" (assert-not-nil "OBJECT_CACHE_MAX")))
      (conj (format "index-parallelism=%s" (assert-not-nil "INDEX_PARALLELISM")))
      ;; Add MEMCACHED_CONFIG
      (cond-> (env-var-exists? "MEMCACHED_HOST")
              (cond->
                true (conj (format "memcached=%s:%s" (assert-not-nil "MEMCACHED_HOST") (assert-not-nil "MEMCACHED_PORT")))

                (env-var-exists? "MEMCACHED_CONFIG_TIMEOUT_IN_MS")
                (conj (format "memcached-config-timeout-msec=%s" (assert-not-nil "MEMCACHED_CONFIG_TIMEOUT_IN_MS")))

                (env-var-exists? "MEMCACHED_AUTO_DISCOVERY")
                (conj (format "memcached-auto-discovery=%s" (assert-not-nil "MEMCACHED_AUTO_DISCOVERY")))

                (env-var-exists? "MEMCACHED_USERNAME")
                (conj (format "memcached-username=%s" (assert-not-nil "MEMCACHED_USERNAME")))

                (env-var-exists? "MEMCACHED_PASSWORD")
                (conj (format "memcached-password=%s" (assert-not-nil "MEMCACHED_PASSWORD")))))
      ;; Add Valcache config
      (cond-> (env-var-exists? "VALCACHE_PATH")
              (cond->
                true (conj (format "valcache-path=%s" (assert-not-nil "VALCACHE_PATH")))
                (env-var-exists? "VALCACHE_MAX_GB")
                (conj (format "valcache-max-gb=%s" (assert-not-nil "VALCACHE_MAX_GB")))))))

(defmulti configure-transactor (fn [env-var-map] (get env-var-map "PROTOCOL")))

(defmethod configure-transactor "DDB-LOCAL"
  [_env-var-map]
  (-> ["pid-file=transactor.pid" "protocol=ddb-local"]
      (conj (format "aws-dynamodb-table=%s" (assert-not-nil "DYNAMO_DB_TABLE")))
      (conj (format "aws-dynamodb-override-endpoint=%s" (assert-not-nil "LOCAL_DYNAMO_DB_ENDPOINT")))
      common-transactor-config))

(defmethod configure-transactor "SQL"
  [_env-var-map]
  (-> ["pid-file=transactor.pid" "protocol=sql"]
      (conj (format "sql-driver-class=%s" (assert-not-nil "SQL_DRIVER_CLASS")))
      (conj (format "sql-url=%s" (assert-not-nil "SQL_JDBC_URL")))
      (conj (format "sql-user=%s" (assert-not-nil "SQL_USER")))
      (conj (format "sql-password=%s" (assert-not-nil "SQL_PASSWORD")))
      common-transactor-config))

(defmethod configure-transactor "DDB"
  [_env-var-map]
  (-> ["pid-file=transactor.pid" "protocol=ddb"]
      (conj (format "aws-dynamodb-table=%s" (assert-not-nil "DYNAMO_DB_TABLE")))
      (conj (format "aws-dynamodb-region=%s" (assert-not-nil "AWS_DYNAMO_DB_REGION")))
      (conj (format "aws-transactor-role=%s" (assert-not-nil "AWS_TRANSACTOR_ROLE")))
      (conj (format "aws-peer-role=%s" (assert-not-nil "AWS_PEER_ROLE")))
      (conj (format "aws-s3-log-bucket-id=%s" (assert-not-nil "AWS_S3_LOG_BUCKET_ID")))
      (conj (format "aws-cloudwatch-dimension-value=%s" (assert-not-nil "AWS_CLOUDWATCH_DIMENSION_VALUE")))
      common-transactor-config))

(defmethod configure-transactor :default
  [env-var-map]
  (println (format "Invalid TRANSACTOR PROTOCOL: %s supplied. SQL, DDB and DDB-LOCAL are the only supported values."
                   (get env-var-map "PROTOCOL")))
  (System/exit 1))

(defmulti configure-run-mode (fn [env-var-map] (get env-var-map "RUN_MODE")))

(defmethod configure-run-mode "TRANSACTOR"
  [env-var-map]
  (let [transactor-config-file "transactor.properties"
        file-appender! (append-to-file)]
    (->> (configure-transactor env-var-map)
         (run! (partial file-appender! transactor-config-file)))
    (->> ["#!/bin/bash"
          (if (#{"DDB" "DDB-LOCAL"} (System/getenv "PROTOCOL"))
            (format "bin/datomic ensure-transactor %s %s"
                  transactor-config-file
                  transactor-config-file)
            "")
          (format "bin/transactor -Xmx\"%s\" -Xms\"%s\" %s"
                  (assert-not-nil "XMX")
                  (assert-not-nil "XMS")
                  transactor-config-file)]
         (run! (partial file-appender! datomic-startup-file)))
    (println "Transactor configuration successfully created in " transactor-config-file)
    (println "Transactor startup file created in " datomic-startup-file)))

(defmethod configure-run-mode "PEER"
  [env-var-map]
  (let [file-appender! (append-to-file)
        extended-peer-configuration
        (-> (StringBuilder.)
            (.append (format "-Ddatomic.txTimeoutMsec=%s -Ddatomic.readConcurrency=%s"
                             (assert-not-nil "PEER_TX_TIMEOUT_IN_MS")
                             (assert-not-nil "PEER_READ_CONCURRENCY")))
            (cond-> (env-var-exists? "MEMCACHED_HOST")
                    (cond->
                      true
                      (.append (format " -Ddatomic.memcachedServers=%s:%s -Ddatomic.memcachedConfigTimeoutMsec=%s"
                                       (assert-not-nil "MEMCACHED_HOST")
                                       (assert-not-nil "MEMCACHED_PORT")
                                       (assert-not-nil "MEMCACHED_CONFIG_TIMEOUT_IN_MS")))

                      (env-var-exists? "MEMCACHED_USERNAME")
                      (.append (format " -Ddatomic.memcachedUsername=%s"
                                       (assert-not-nil "MEMCACHED_USERNAME")))

                      (env-var-exists? "MEMCACHED_PASSWORD")
                      (.append (format " -Ddatomic.memcachedPassword=%s"
                                       (assert-not-nil "MEMCACHED_PASSWORD")))

                      (env-var-exists? "MEMCACHED_AUTO_DISCOVERY")
                      (.append (format " -Ddatomic.memcachedAutoDiscovery=%s"
                                       (assert-not-nil "MEMCACHED_AUTO_DISCOVERY")))))

            (cond-> (env-var-exists? "VALCACHE_PATH")
                    (cond->
                      true
                      (.append (format " -Ddatomic.valcachePath=%s"
                                       (assert-not-nil "VALCACHE_PATH")))

                      (env-var-exists? "VALCACHE_MAX_GB")
                      (.append (format " -Ddatomic.valcacheMaxGb=%s"
                                       (assert-not-nil "VALCACHE_MAX_GB")))))

            (.toString))]
    (->> ["#!/bin/bash"
          (format "bin/run -Xmx\"%s\" -Xms\"%s\" \"%s\" \\"
                  (assert-not-nil "XMX")
                  (assert-not-nil "XMS")
                  extended-peer-configuration)
          "-m datomic.peer-server \\"
          (format "-h \"%s\" \\"
                  (assert-not-nil "PEER_HOST"))
          (format "-p \"%s\" \\"
                  (assert-not-nil "PEER_PORT"))
          (format " -a \"%s\",\"%s\" \\"
                  (assert-not-nil "PEER_ACCESSKEY")
                  (assert-not-nil "PEER_SECRET"))
          (format "-c \"%s\" \\"
                  (assert-not-nil "PEER_CONCURRENCY"))
          (format "-d \"%s\",\"%s\""
                  (assert-not-nil "DATOMIC_DB_NAME")
                  (datomic-uri env-var-map))]
         (run! (partial file-appender! datomic-startup-file)))
    (println (slurp "datomic_start_up.sh"))
    (println "PEER startup file created in " datomic-startup-file)))

(defmethod configure-run-mode "BACKUP_DB"
  [env-var-map]
  (let [file-appender! (append-to-file)]
    (->> ["#!/bin/bash"
          (format "bin/datomic -Xmx\"%s\" -Xms\"%s\" backup-db \"%s\" \"%s\""
                  (assert-not-nil "XMX")
                  (assert-not-nil "XMS")
                  (datomic-uri env-var-map)
                  (assert-not-nil "BACKUP_S3_BUCKET_URI"))]
         (run! (partial file-appender! datomic-startup-file)))))

(defmethod configure-run-mode "LIST_BACKUPS"
  [_]
  (let [file-appender! (append-to-file)]
    (->> ["#!/bin/bash"
          (format "bin/datomic -Xmx\"%s\" -Xms\"%s\" list-backups \"%s\""
                  (assert-not-nil "XMX")
                  (assert-not-nil "XMS")
                  (assert-not-nil "BACKUP_S3_BUCKET_URI"))]
         (run! (partial file-appender! datomic-startup-file)))))

(defmethod configure-run-mode "VERIFY_BACKUP"
  [_]
  (let [file-appender! (append-to-file)]
    (->> ["#!/bin/bash"
          (format "bin/datomic -Xmx\"%s\" -Xms\"%s\" verify-backup \"%s\" \"%s\" \"%s\""
                  (assert-not-nil "XMX")
                  (assert-not-nil "XMS")
                  (assert-not-nil "BACKUP_S3_BUCKET_URI")
                  (and (env-var-exists? "VERIFY_ALL_SEGMENTS")
                       (-> (assert-not-nil "VERIFY_ALL_SEGMENTS")
                           (str/trim)
                           (str/lower-case)
                           (= "true")))
                  (assert-not-nil "BACKUP_T_VALUE"))]
         (run! (partial file-appender! datomic-startup-file)))))

(defmethod configure-run-mode "RESTORE_DB"
  [env-var-map]
  (let [file-appender! (append-to-file)]
    (->> ["#!/bin/bash"
          (format "bin/datomic -Xmx\"%s\" -Xms\"%s\" restore-db \"%s\" \"%s\" \"%s\""
                  (assert-not-nil "XMX")
                  (assert-not-nil "XMS")
                  (assert-not-nil "BACKUP_S3_BUCKET_URI")
                  (datomic-uri env-var-map)
                  (if (env-var-exists? "BACKUP_T_VALUE")
                    (System/getenv "BACKUP_T_VALUE")
                    ""))]
         (run! (partial file-appender! datomic-startup-file)))))

(defmethod configure-run-mode "CONSOLE"
  [env-var-map]
  (let [file-appender! (append-to-file)]
    (->> ["#!/bin/bash"
          (format "bin/console -p \"%s\" \"%s\" \"%s\""
                  (assert-not-nil "DATOMIC_CONSOLE_PORT")
                  (assert-not-nil "TRANSACTOR_ALIAS")
                  (datomic-uri-for-console env-var-map))]
         (run! (partial file-appender! datomic-startup-file)))))

(defmethod configure-run-mode :default
  [env-var-map]
  (println (format "Invalid RUN_MODE: %s supplied.\nTRANSACTOR, PEER, BACKUP_DB, LIST_BACKUPS, VERIFY_BACKUP, RESTORE_DB, CONSOLE are the only supported values."
                   (get env-var-map "RUN_MODE")))
  (System/exit 1))

;; Start config generator
(try
  (configure-run-mode (System/getenv))
  (make-script-executable datomic-startup-file)
  (catch Throwable t
    (println "Couldn't build bootstrap configuration for Datomic")
    (println (Throwable->map t))
    (System/exit 1)))
