(ns com.terry.circuit-breaker-data-source.data-source-proxy-test
  (:require [clojure.test :refer :all]
            [com.terry.circuit-breaker-data-source.test-data-source :as tds]
            [com.terry.circuit-breaker-data-source.data-source-proxy :as dsp])
  (:import (java.io ByteArrayOutputStream PrintWriter)
           (java.sql SQLException)
           (com.mchange.v2.resourcepool TimeoutException)
           (com.netflix.hystrix Hystrix)))

(defn test-setup [f]
  (Hystrix/reset)
  (f))

(use-fixtures :each test-setup)

(deftest test-interface
  (let [target-ds (tds/h2-data-source)
        proxy-ds (dsp/circuit-breaker-data-source target-ds {:group-key "test-group-1"
                                                             :command-key "test-1"})
        ps (PrintWriter. (ByteArrayOutputStream.))]
    (is (not (nil? (.getConnection proxy-ds))))
    (is (not (nil? (.getConnection proxy-ds "" ""))))
    (.setLogWriter proxy-ds ps)
    (is (= ps (.getLogWriter proxy-ds)))
    (is (not (nil? (.getParentLogger proxy-ds))))
    (.setLoginTimeout proxy-ds 60)
    (is (= 60 (.getLoginTimeout proxy-ds)))
    (try
      ;c3p0 will throw an exception here
      (.unwrap proxy-ds (.getClass (String.)))
      (catch Exception e
        (is (instance? SQLException e))))
    (is (= false (.isWrapperFor proxy-ds (.getClass (String.)))))))


(deftest test-exception-propagated
  (let [target-ds (tds/failing-data-source)
        proxy-ds (dsp/circuit-breaker-data-source target-ds {:group-key "test-group-2"
                                                             :command-key "test-2"
                                                             :execution-isolation-thread-timeout-in-milliseconds 2000})]
    (try
      (.getConnection proxy-ds)
      (catch Exception e
        (is (instance? TimeoutException e) "Is the exception thrown by the wrapped datasource propagated?")
        ))))

(deftest test-circuit-breaks
  (let [circuit-breaker-settings {:group-key                                          "test-group-3"
                                  :command-key "test-3"
                                  :circuit-breaker-request-volume-threshold           5
                                  :circuit-breaker-sleep-window-in-milliseconds       3000
                                  :execution-isolation-thread-timeout-in-milliseconds 2000
                                  :metrics-rolling-stats-time-in-milliseconds         10000}]
    (let [target-ds (tds/failing-data-source)
          proxy-ds (dsp/circuit-breaker-data-source target-ds circuit-breaker-settings)
          results (mapv
                    (fn [i]
                      (try
                        (.getConnection proxy-ds)
                        (catch Exception e e)))
                    (range 20))]
      (is (= 20 (count results)))
      (is (<= 5 (count (filter (fn [e] (instance? TimeoutException e)) results))))
      (is (<= 14 (count (filter (fn [e]
                                  (and (instance? RuntimeException e)
                                       (= "Hystrix circuit short-circuited and is OPEN" (.getMessage e))))
                                results)))))
    (let [target-ds (tds/h2-data-source)
          proxy-ds (dsp/circuit-breaker-data-source target-ds circuit-breaker-settings)]
      (is (= true (try
                    (.getConnection proxy-ds)
                    false
                    (catch Exception e true)))
          "Circuit should still be open as same group and command key is used")
      (Thread/sleep 3500)
      (is (= true (try
                    (.getConnection proxy-ds)
                    true
                    (catch Exception e false)))
          "Circuit should be closed as same group and command key is used and we have waited long enough for it to retry")
      )))