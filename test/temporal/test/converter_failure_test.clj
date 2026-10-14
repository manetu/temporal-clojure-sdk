(ns temporal.test.converter-failure-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow]]))

(use-fixtures :once t/wrap-service)

(defactivity failing-serialization-activity
  [_ctx _args]
  (throw (ex-info "boom" {:obj (Object.)})))

(defworkflow failing-serialization-workflow
  [args]
  @(a/invoke failing-serialization-activity args {:retry-options {:maximum-attempts 1}}))

(defn- exception-messages
  [e]
  (->> (iterate ex-cause e)
       (take-while some?)
       (mapcat (fn [x]
                 (keep identity [(ex-message x) (str x)])))))

(deftest non-serializable-failure-details-do-not-time-out
  (testing "Verifies that activity failures with non-serializable details do not degrade into StartToClose timeouts"
    (let [workflow (t/create-workflow failing-serialization-workflow)]
      (c/start workflow {})
      (let [e (try
                @(c/get-result workflow)
                (catch Exception e
                  e))
            messages (exception-messages e)]
        (is (instance? Exception e))
        (is (some #(str/includes? % "Failed to freeze type: class java.lang.Object") messages))
        (is (not-any? #(str/includes? % "Activity task timed out") messages))
        (is (not-any? #(str/includes? % "TIMEOUT_TYPE_START_TO_CLOSE") messages))))))
