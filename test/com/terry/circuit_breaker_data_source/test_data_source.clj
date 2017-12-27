(ns com.terry.circuit-breaker-data-source.test-data-source
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)
           (javax.sql DataSource)
           (com.mchange.v2.resourcepool TimeoutException)))

(defn create-data-source
  [{:keys [driver-class-name url user password max-connections]}]
  (let [data-source (doto (ComboPooledDataSource.)
                      (.setDriverClass driver-class-name)
                      (.setJdbcUrl url)
                      (.setUser user)
                      (.setPassword password)
                      (.setMinPoolSize 1)
                      (.setMaxPoolSize max-connections)
                      (.setAcquireIncrement 1)
                      (.setPreferredTestQuery "select 1"))]
    data-source))

(defn h2-data-source []
  (create-data-source {:url "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MYSQL"
                       :user ""
                       :password ""
                       :driver-class-name "org.h2.Driver"
                       :max-connections 1}))

(defn failing-data-source []
  (reify DataSource
    (getConnection [this]
      (Thread/sleep 1000)
      (throw (TimeoutException. "Test!")))
    (getConnection [this username password]
      (Thread/sleep 1000)
      (throw (TimeoutException. "Test!")))
    (isWrapperFor [this iface]
      false)
    (unwrap [this iface])
    (getLogWriter [this]
      nil)
    (setLogWriter [this out])
    (setLoginTimeout [this seconds])
    (getLoginTimeout [this]
      nil)
    (getParentLogger [this]
      nil)))