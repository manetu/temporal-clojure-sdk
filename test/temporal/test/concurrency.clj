;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.concurrency
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.promise :as pt]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity concurrency-activity
  [ctx args]
  (log/info "activity:" args)
  args)

(defn invoke [x]
  (a/invoke concurrency-activity x))

(defworkflow concurrency-workflow
  [args]
  (log/info "workflow:" args)
  @(-> (pt/all (map invoke (range 10)))
       (p/then (fn [r]
                 (log/info "r:" r)
                 r))))

(deftest the-test
  (testing "Verifies that we can launch activities in parallel"
    (let [workflow (t/create-workflow concurrency-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 10))))))