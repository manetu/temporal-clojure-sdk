(ns temporal.sample.batch-processor.core
  "Demonstrates child workflows and parallel processing patterns.

  This sample shows:
  - Using child workflows for parallel fan-out processing
  - Aggregating results from multiple child workflows
  - Using temporal.promise/all for concurrent execution
  - Using external-workflow to signal between workflows"
  (:require [taoensso.timbre :as log]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.promise :as tp]
            [temporal.signals :as s])
  (:import [java.time Duration]))

(def task-queue "batch-processor-queue")

;; -----------------------------------------------------------------------------
;; Activities
;; -----------------------------------------------------------------------------

(defactivity process-item
  "Processes a single item. Simulates variable processing time."
  [_ctx {:keys [item-id data]}]
  (log/info "Processing item:" item-id)
  ;; Simulate variable processing time
  (Thread/sleep (+ 100 (rand-int 200)))
  (let [result {:item-id item-id
                :processed-data (str "Processed: " data)
                :processed-at (System/currentTimeMillis)}]
    (log/info "Completed item:" item-id)
    result))

(defactivity aggregate-results
  "Aggregates results from all processed items."
  [_ctx {:keys [batch-id results]}]
  (log/info "Aggregating" (count results) "results for batch:" batch-id)
  {:batch-id batch-id
   :total-items (count results)
   :completed-at (System/currentTimeMillis)
   :summary (mapv :item-id results)})

;; -----------------------------------------------------------------------------
;; Child Workflow - processes a single item
;; -----------------------------------------------------------------------------

(def activity-opts {:start-to-close-timeout (Duration/ofSeconds 30)})

(defworkflow item-processor-workflow
  "Child workflow that processes a single item.
  Using a child workflow per item provides:
  - Independent retry policies per item
  - Separate execution history
  - Ability to query individual item status"
  [{:keys [item-id data notify-workflow-id]}]
  (log/info "Item processor workflow started for:" item-id)

  (let [result @(a/invoke process-item
                          {:item-id item-id :data data}
                          activity-opts)]

    ;; If a parent workflow ID was provided, signal completion
    (when notify-workflow-id
      (let [parent (w/external-workflow notify-workflow-id)]
        (w/signal-external parent ::item-completed result)))

    result))

;; -----------------------------------------------------------------------------
;; Parent Workflow - orchestrates batch processing
;; -----------------------------------------------------------------------------

(defworkflow batch-processor-workflow
  "Parent workflow that orchestrates processing of a batch of items.

  Demonstrates two patterns:
  1. Parallel fan-out using child workflows with promise/all
  2. Signal-based completion notification using external-workflow"
  [{:keys [batch-id items use-signals?]}]
  (log/info "Batch processor started for batch:" batch-id
            "with" (count items) "items")

  (if use-signals?
    ;; Pattern 2: Use signals for completion notification
    ;; Useful when you want to track progress as items complete
    (let [signals (s/create-signal-chan)
          results (atom [])
          {:keys [workflow-id]} (w/get-info)]

      ;; Register update handler to track progress
      (w/register-update-handler!
       (fn [update-type _args]
         (case update-type
           :get-progress {:completed (count @results)
                          :total (count items)}
           nil)))

      ;; Start all child workflows
      (log/info "Starting child workflows with signal notification...")
      (doseq [{:keys [id data]} items]
        (w/invoke item-processor-workflow
                  {:item-id id
                   :data data
                   :notify-workflow-id workflow-id}
                  {:task-queue task-queue}))

      ;; Wait for all items to complete via signals
      (log/info "Waiting for completion signals...")
      (dotimes [_ (count items)]
        (when-let [result (s/<! signals ::item-completed)]
          (log/info "Received completion for item:" (:item-id result))
          (swap! results conj result)))

      ;; Aggregate results
      (log/info "All items completed, aggregating...")
      @(a/invoke aggregate-results
                 {:batch-id batch-id :results @results}
                 activity-opts))

    ;; Pattern 1: Use promise/all for parallel execution
    ;; Simpler when you just need all results at the end
    (do
      (log/info "Starting child workflows with promise/all...")
      (let [child-promises (mapv (fn [{:keys [id data]}]
                                   (w/invoke item-processor-workflow
                                             {:item-id id :data data}
                                             {:task-queue task-queue}))
                                 items)
            ;; Wait for all children to complete
            results @(tp/all child-promises)]

        (log/info "All child workflows completed")

        ;; Aggregate results
        @(a/invoke aggregate-results
                   {:batch-id batch-id :results results}
                   activity-opts)))))
