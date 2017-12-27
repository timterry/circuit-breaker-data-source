(defproject circuit-breaker-data-source "0.1.0-SNAPSHOT"
  :description "A datasource proxy that protects the get connection method with the netflix hystrix circuit breaker"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.netflix.hystrix/hystrix-clj "1.5.12"]]
  :profiles {:dev {:resource-paths ["test-resources"]
                   :dependencies [[com.h2database/h2 "1.4.188"]
                                  [com.mchange/c3p0 "0.9.5-pre8"]
                                  [ch.qos.logback/logback-classic "1.1.7"]]}})
