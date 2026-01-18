(ns temporal.sample.signals-queries.core
  "Demonstrates signals and queries for workflow communication.

  This sample shows:
  - Using signal channels to receive messages
  - Registering query handlers to inspect workflow state
  - Sending signals to running workflows
  - Querying workflow state from the client"
  (:require [taoensso.timbre :as log]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.signals :as s])
  (:import [java.time Duration]))

;; -----------------------------------------------------------------------------
;; Workflow
;; -----------------------------------------------------------------------------

(defworkflow order-workflow
  "A workflow that processes an order and accepts signals to update its status.

  The workflow:
  1. Starts in 'pending' status
  2. Waits for signals to update the order
  3. Exposes current state via query handler
  4. Completes when it receives a 'complete' or 'cancel' signal"
  [{:keys [order-id items]}]
  (log/info "Starting order workflow:" order-id)

  ;; Workflow state - tracks order status and items
  (let [state (atom {:order-id order-id
                     :items items
                     :status :pending
                     :notes []})

        ;; Create a signal channel for receiving messages
        signals (s/create-signal-chan)]

    ;; Register a query handler so clients can inspect the order state
    (w/register-query-handler!
     (fn [query-type args]
       (case query-type
         :get-status (:status @state)
         :get-order @state
         nil)))

    ;; Process signals until the order is completed or cancelled
    (loop []
      (log/info "Waiting for signal... Current status:" (:status @state))

      ;; Block waiting for any signal (with 30 second timeout for demo)
      (when-let [signal (s/<! signals ::order-signal (Duration/ofSeconds 30))]
        (let [{:keys [action note]} signal]
          (log/info "Received signal:" action)

          (case action
            :add-note
            (do
              (swap! state update :notes conj note)
              (recur))

            :ship
            (do
              (swap! state assoc :status :shipped)
              (recur))

            :complete
            (swap! state assoc :status :completed)

            :cancel
            (swap! state assoc :status :cancelled)

            ;; Unknown action - continue waiting
            (recur)))))

    ;; Return final state
    (log/info "Order workflow completed with status:" (:status @state))
    @state))
