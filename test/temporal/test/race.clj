;; Copyright © 2022 Manetu, Inc.  All rights reserved
(ns temporal.test.race
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go <!] :as async]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.promise :as pt]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity delayed-activity
  [ctx {:keys [id delay] :as args}]
  (log/info "activity:" args)
  (go
    (<! (async/timeout delay))
    id))

(defn invoke [x]
  (a/invoke delayed-activity x))

(defworkflow race-workflow
  [ctx {:keys [args]}]
  (log/info "workflow:" args)
  ;; invoke activities with various synthetic delays.  The last entry, index 4, should be the fastest
  @(-> (pt/race (map-indexed (fn [i x] (invoke {:id i :delay x})) [600 400 200 100 10]))
       (p/then (fn [r]
                 (log/info "r:" r)
                 r))))

(deftest the-test
  (testing "Verifies that we can launch activities in parallel"
    (let [workflow (t/create-workflow race-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref (= 4))))))
