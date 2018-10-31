(defproject clojpg "0.1.0-SNAPSHOT"
  :description "REST service example"
  :url "http://someurl"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.6.1"]
                 [ring/ring-json "0.1.2"]
                 [com.mchange/c3p0 "0.9.5"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [org.postgresql/postgresql "42.2.5"]
                 [cheshire "4.0.3"]
                 [ring/ring-mock "0.3.2"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [clj-time "0.11.0"]]
  :plugins [[lein-ring "0.7.3"]]
  :ring {:handler clojpg.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]]}})
