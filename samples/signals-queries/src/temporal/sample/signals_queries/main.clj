(ns temporal.sample.signals-queries.main
  "Entry point for the Signals and Queries sample.

  Prerequisites:
  - A running Temporal server (e.g., `temporal server start-dev`)

  Run with: lein run"
  (:require [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.sample.signals-queries.core :refer [order-workflow]]
            [environ.core :refer [env]])
  (:gen-class))

(def task-queue "signals-queries-queue")

(defn -main
  "Demonstrates sending signals and queries to a running workflow."
  [& _args]
  (log/info "Starting Signals and Queries sample...")

  (let [client (c/create-client {:target (env :temporal-target "127.0.0.1:7233")
                                 :namespace (env :temporal-namespace "default")})
        w (worker/start client {:task-queue task-queue})]

    (try
      ;; Create and start the order workflow
      (let [workflow (c/create-workflow client order-workflow
                                        {:task-queue task-queue
                                         :workflow-id "order-12345"})]

        (log/info "Starting order workflow...")
        (c/start workflow {:order-id "12345"
                           :items [{:sku "WIDGET-1" :qty 2}
                                   {:sku "GADGET-5" :qty 1}]})

        ;; Give the workflow a moment to start
        (Thread/sleep 500)

        ;; Query the initial status
        (log/info "===================================")
        (log/info "Querying order status...")
        (let [status (c/query workflow :get-status {})]
          (log/info "Current status:" status))

        ;; Send a signal to add a note
        (log/info "===================================")
        (log/info "Sending signal: add-note")
        (c/>! workflow ::order-signal {:action :add-note
                                       :note "Customer requested gift wrapping"})
        (Thread/sleep 500)

        ;; Query the full order state
        (log/info "Querying full order state...")
        (let [order (c/query workflow :get-order {})]
          (log/info "Order state:" order))

        ;; Send signal to ship
        (log/info "===================================")
        (log/info "Sending signal: ship")
        (c/>! workflow ::order-signal {:action :ship})
        (Thread/sleep 500)

        ;; Query status again
        (let [status (c/query workflow :get-status {})]
          (log/info "Status after shipping:" status))

        ;; Complete the order
        (log/info "===================================")
        (log/info "Sending signal: complete")
        (c/>! workflow ::order-signal {:action :complete})

        ;; Wait for the result
        (let [result @(c/get-result workflow)]
          (log/info "===================================")
          (log/info "Final order state:" result)
          (log/info "===================================")))

      (finally
        (worker/stop w)))

    (log/info "Sample completed.")
    (System/exit 0)))
