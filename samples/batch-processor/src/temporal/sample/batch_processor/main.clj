(ns temporal.sample.batch-processor.main
  "Entry point for the Batch Processor sample.

  Prerequisites:
  - A running Temporal server (e.g., `temporal server start-dev`)

  Run with: lein run"
  (:require [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.sample.batch-processor.core :refer [batch-processor-workflow
                                                          task-queue]]
            [environ.core :refer [env]])
  (:gen-class))

(def sample-items
  "Sample batch of items to process"
  [{:id "ITEM-001" :data "First item data"}
   {:id "ITEM-002" :data "Second item data"}
   {:id "ITEM-003" :data "Third item data"}
   {:id "ITEM-004" :data "Fourth item data"}
   {:id "ITEM-005" :data "Fifth item data"}])

(defn -main
  "Demonstrates parallel batch processing patterns."
  [& _args]
  (log/info "Starting Batch Processor sample...")

  (let [client (c/create-client {:target (env :temporal-target "127.0.0.1:7233")
                                 :namespace (env :temporal-namespace "default")})
        w (worker/start client {:task-queue task-queue})]

    (try
      ;; --- Pattern 1: Using promise/all ---
      (log/info "")
      (log/info "==============================================")
      (log/info "Pattern 1: Parallel Processing with promise/all")
      (log/info "==============================================")
      (log/info "")

      (let [workflow (c/create-workflow client batch-processor-workflow
                                        {:task-queue task-queue
                                         :workflow-id "batch-promise-all"})]

        (c/start workflow {:batch-id "BATCH-001"
                           :items sample-items
                           :use-signals? false})

        (let [result @(c/get-result workflow)]
          (log/info "")
          (log/info "Batch result:" result)))

      ;; --- Pattern 2: Using signals for progress tracking ---
      (log/info "")
      (log/info "==============================================")
      (log/info "Pattern 2: Progress Tracking with Signals")
      (log/info "==============================================")
      (log/info "")

      (let [workflow (c/create-workflow client batch-processor-workflow
                                        {:task-queue task-queue
                                         :workflow-id "batch-with-signals"})]

        (c/start workflow {:batch-id "BATCH-002"
                           :items sample-items
                           :use-signals? true})

        ;; Query progress while processing
        (Thread/sleep 500)
        (log/info "Querying progress...")
        (let [progress (c/update workflow :get-progress {})]
          (log/info "Current progress:" progress))

        (let [result @(c/get-result workflow)]
          (log/info "")
          (log/info "Batch result:" result)))

      (log/info "")
      (log/info "==============================================")
      (log/info "Batch Processor sample completed.")
      (log/info "==============================================")

      (finally
        (worker/stop w)))

    (System/exit 0)))
