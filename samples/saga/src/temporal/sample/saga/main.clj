(ns temporal.sample.saga.main
  "Entry point for the Saga sample.

  Prerequisites:
  - A running Temporal server (e.g., `temporal server start-dev`)

  Run with: lein run"
  (:require [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.sample.saga.core :refer [trip-booking-saga]]
            [environ.core :refer [env]])
  (:gen-class))

(def task-queue "saga-queue")

(defn -main
  "Demonstrates the Saga pattern with success and failure cases."
  [& _args]
  (log/info "Starting Saga sample...")

  (let [client (c/create-client {:target (env :temporal-target "127.0.0.1:7233")
                                 :namespace (env :temporal-namespace "default")})
        w (worker/start client {:task-queue task-queue})]

    (try
      ;; --- Example 1: Successful booking ---
      (log/info "")
      (log/info "===========================================")
      (log/info "Example 1: Successful Trip Booking")
      (log/info "===========================================")

      (let [workflow (c/create-workflow client trip-booking-saga
                                        {:task-queue task-queue
                                         :workflow-id "trip-success-123"})]

        (c/start workflow {:trip-id "TRIP-001"
                           :destination "Paris"
                           :nights 5
                           :fail-car? false})

        (let [result @(c/get-result workflow)]
          (log/info "")
          (log/info "Trip booking result:" result)))

      ;; --- Example 2: Failed booking with compensation ---
      (log/info "")
      (log/info "===========================================")
      (log/info "Example 2: Failed Booking with Compensation")
      (log/info "===========================================")

      (let [workflow (c/create-workflow client trip-booking-saga
                                        {:task-queue task-queue
                                         :workflow-id "trip-fail-456"})]

        (c/start workflow {:trip-id "TRIP-002"
                           :destination "Tokyo"
                           :nights 7
                           :fail-car? true})  ;; This will cause the car booking to fail

        (let [result @(c/get-result workflow)]
          (log/info "")
          (log/info "Trip booking result:" result)))

      (log/info "")
      (log/info "===========================================")
      (log/info "Saga sample completed.")
      (log/info "===========================================")

      (finally
        (worker/stop w)))

    (System/exit 0)))
