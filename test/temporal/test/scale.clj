;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.scale
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go <!] :as async]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity scale-activity
  [ctx {:keys [id] :as args}]
  (log/info "activity:" args)
  (go
    (<! (async/timeout 1000))
    id))

(defworkflow scale-workflow
  [ctx {:keys [args]}]
  (log/info "workflow:" args)
  @(a/invoke scale-activity args))

(defn create [id]
  (let [workflow (t/create-workflow scale-workflow)]
    (c/start workflow {:id id})
    (c/get-result workflow)))

(deftest the-test
  (testing "Verifies that we can launch workflows in parallel"
    (let [nr 1000]
      (time
       (is (-> @(p/all (map create (range nr))) count (= nr)))))))
