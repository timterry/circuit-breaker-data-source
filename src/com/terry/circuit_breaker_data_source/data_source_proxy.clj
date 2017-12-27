(ns com.terry.circuit-breaker-data-source.data-source-proxy
  (:require [com.netflix.hystrix.core :as hystrix])
  (:import [javax.sql DataSource]
           (com.netflix.hystrix HystrixCommand$Setter HystrixCommandProperties HystrixCommandProperties$Setter HystrixThreadPoolProperties)
           (com.netflix.hystrix.exception HystrixRuntimeException)))


(defn create-init-function
  "Full config params can be found here: https://github.com/Netflix/Hystrix/wiki/Configuration"
  [{:keys [execution-isolation-thread-timeout-in-milliseconds
           execution-timeout-enabled
           circuit-breaker-request-volume-threshold
           circuit-breaker-sleep-window-in-milliseconds
           circuit-breaker-error-threshold-percentage
           request-cache-enabled
           thread-pool-core-size
           thread-pool-maximum-size
           thread-pool-max-queue-size
           thread-pool-keep-aliveTime-minutes
           thread-pool-allow-maximum-size-to-diverge-from-core-size
           metrics-rolling-stats-time-in-milliseconds] :as circuit-breaker-settings}]
  (let [hystrix-cmd-properties-setter
        (cond-> (HystrixCommandProperties/Setter)

                execution-isolation-thread-timeout-in-milliseconds
                (.withExecutionTimeoutInMilliseconds execution-isolation-thread-timeout-in-milliseconds)

                execution-timeout-enabled
                (.withExecutionTimeoutEnabled execution-timeout-enabled)

                circuit-breaker-request-volume-threshold
                (.withCircuitBreakerRequestVolumeThreshold circuit-breaker-request-volume-threshold)

                circuit-breaker-sleep-window-in-milliseconds
                (.withCircuitBreakerSleepWindowInMilliseconds circuit-breaker-sleep-window-in-milliseconds)

                request-cache-enabled
                (.withRequestCacheEnabled request-cache-enabled)

                circuit-breaker-error-threshold-percentage
                (.withCircuitBreakerErrorThresholdPercentage circuit-breaker-error-threshold-percentage)

                metrics-rolling-stats-time-in-milliseconds
                (.withMetricsRollingStatisticalWindowInMilliseconds metrics-rolling-stats-time-in-milliseconds))

        hystrix-thread-pool-properties-setter
        (cond-> (HystrixThreadPoolProperties/Setter)

                thread-pool-core-size
                (.withCoreSize thread-pool-core-size)

                thread-pool-maximum-size
                (.withMaximumSize thread-pool-maximum-size)

                thread-pool-max-queue-size
                (.withMaxQueueSize thread-pool-max-queue-size)

                thread-pool-keep-aliveTime-minutes
                (.withKeepAliveTimeMinutes thread-pool-keep-aliveTime-minutes)

                thread-pool-allow-maximum-size-to-diverge-from-core-size
                (.withAllowMaximumSizeToDivergeFromCoreSize thread-pool-allow-maximum-size-to-diverge-from-core-size))]
    (fn [_ ^HystrixCommand$Setter setter]
      (-> setter
        (.andCommandPropertiesDefaults hystrix-cmd-properties-setter)
        (.andThreadPoolPropertiesDefaults hystrix-thread-pool-properties-setter)))))

(defn create-hystrix-command [circuit-breaker-settings protected-fn]
  (hystrix/command {:type :command
                    :group-key (:group-key circuit-breaker-settings)
                    :command-key (or (:command-key circuit-breaker-settings) :sql-connection)
                    :run-fn protected-fn
                    :fallback-fn nil
                    :cache-key-fn nil
                    :init-fn (:init-fn circuit-breaker-settings)}))

(defn protect-get-connection [target-data-source circuit-breaker-settings]
  (let [hystrix-cmd (create-hystrix-command circuit-breaker-settings #(.getConnection ^DataSource %))]
    (try
      (hystrix/execute hystrix-cmd target-data-source)
      (catch HystrixRuntimeException hre
        (if-let [cause (.getCause hre)]
          (throw cause)
          (throw hre))))))

(defn protect-get-connection-with-creds [target-data-source circuit-breaker-settings username password]
  (let [hystrix-cmd (create-hystrix-command circuit-breaker-settings (fn[^DataSource a b c](.getConnection a b c)))]
    (try
      (hystrix/execute hystrix-cmd target-data-source username password)
      (catch HystrixRuntimeException hre
        (if-let [cause (.getCause hre)]
          (throw cause)
          (throw hre))))))

(defn circuit-breaker-data-source
  [^DataSource target-data-source circuit-breaker-settings]
  (let [hystrix-init-function (create-init-function circuit-breaker-settings)
        settings (assoc circuit-breaker-settings :init-fn hystrix-init-function)]
    (reify DataSource
      (getConnection [this]
        (protect-get-connection target-data-source settings))
      (getConnection [this username password]
        (protect-get-connection-with-creds target-data-source settings username password))
      (isWrapperFor [this iface]
        (.isWrapperFor target-data-source iface))
      (unwrap [this iface]
        (.unwrap target-data-source iface))
      (getLogWriter [this]
        (.getLogWriter target-data-source))
      (setLogWriter [this out]
        (.setLogWriter target-data-source out))
      (setLoginTimeout [this seconds]
        (.setLoginTimeout target-data-source seconds))
      (getLoginTimeout [this]
        (.getLoginTimeout target-data-source))
      (getParentLogger [this]
        (.getParentLogger target-data-source)))))



