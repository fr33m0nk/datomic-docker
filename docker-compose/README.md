# Docker compose for Datomic Database with PostgreSQL backend

## Docker compose file provides
- Memcached
- Postgres
- Init container for Postgres
  - This container executes migrations needed for Datomic
- Datomic transactor
- Init container for Datomic
  - This container creates a database in Datomic system
  - This step is needed for the Peer to start
- Datomic peer
- Datomic console
  - Accessible at `http://localhost:${CONSOLE_HOST_MAPPING_PORT}`
  - Default value for `CONSOLE_HOST_MAPPING_PORT` is `8888`

## The configuration variables for docker compose file are in [`.env`](./.env) file

## Usage with SQL backend

### Starting the docker compose 
1. Start containers
```bash
DOCKER_BUILDKIT=1 docker compose up \
  --build \
  transactor \
  datomic-db-initialization \
  peer \
  console \
  --force-recreate \
  --remove-orphans \
  -d
```
2. Connect via Datomic peer
```clojure
(require '[datomic.api :as d])
;; nil

;; assuming DATOMIC DB name is stored in environment variable `DATOMIC_DB_NAME`
(def db-uri (format "datomic:sql://%s?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic" (System/getenv "DATOMIC_DB_NAME")))
;; #'user/db-uri

user=> (d/connect db-uri)
;; #object[datomic.peer.Connection 0x738da6f2 "{:unsent-updates-queue 0, :pending-txes 0, :next-t 1000, :basis-t 66, :index-rev 0, :db-id \"hello-275c1f24-482a-4ae2-86a8-844555f04f46\"}"]

```
3. Connect via Datomic client
```clojure
(require '[datomic.client.api :as dc])

(def client (dd/client {:server-type :peer-server
                        :access-key "myaccesskey" ;; PEER_ACCESSKEY
                        :secret "mysecret" ;; PEER_SECRET
                        :endpoint "localhost:8998"
                        :validate-hostnames false}))
;; #'user/client

user=> (dc/connect client {:db-name "test-database"}) ;; DATOMIC_DB_NAME
{:db-name "hello", :database-id "hello-275c1f24-482a-4ae2-86a8-844555f04f46", :t 66, :next-t 1000, :type :datomic.client/conn}

```

## Usage with Scylla DB backend

### Starting the docker compose

```bash
DOCKER_BUILDKIT=1 docker compose \
  -f ./docker-compose-scylla-db.yml \
  up \
  --build \
  transactor \
  datomic-db-initialization \
  peer \
  console \
  --force-recreate \
  --remove-orphans \
  -d
```

2. Connect via Datomic peer
```clojure
(require '[datomic.api :as d])
;; nil

;; assuming DATOMIC DB name is stored in environment variable `DATOMIC_DB_NAME`
(def db-uri (format "datomic:ddb-local://localhost:8000/%s" (System/getenv "DATOMIC_DB_NAME")))
;; #'user/db-uri

user=> (d/connect db-uri)
;; #object[datomic.peer.Connection 0x738da6f2 "{:unsent-updates-queue 0, :pending-txes 0, :next-t 1000, :basis-t 66, :index-rev 0, :db-id \"hello-275c1f24-482a-4ae2-86a8-844555f04f46\"}"]

```
3. Connect via Datomic client
```clojure
(require '[datomic.client.api :as dc])

(def client (dd/client {:server-type :peer-server
                        :access-key "myaccesskey" ;; PEER_ACCESSKEY
                        :secret "mysecret" ;; PEER_SECRET
                        :endpoint "localhost:8998"
                        :validate-hostnames false}))
;; #'user/client

user=> (dc/connect client {:db-name "test-database"}) ;; DATOMIC_DB_NAME
{:db-name "hello", :database-id "hello-275c1f24-482a-4ae2-86a8-844555f04f46", :t 66, :next-t 1000, :type :datomic.client/conn}

```

### Stopping the docker compose
```bash
docker compose down
```

### Complete cleanup (persistent volumes would get deleted!!)
```bash
docker compose down --remove-orphans -t 1 || true && docker compose down -t 1 || true && docker compose -f docker-compose.yml down --volumes -t 1
```
