(ns temporal.sample.updates.main
  "Entry point for the Updates sample.

  Prerequisites:
  - A running Temporal server (e.g., `temporal server start-dev`)

  Run with: lein run"
  (:require [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.sample.updates.core :refer [counter-workflow]]
            [environ.core :refer [env]])
  (:gen-class))

(def task-queue "updates-queue")

(defn -main
  "Demonstrates various update patterns."
  [& _args]
  (log/info "Starting Updates sample...")

  (let [client (c/create-client {:target (env :temporal-target "127.0.0.1:7233")
                                 :namespace (env :temporal-namespace "default")})
        w (worker/start client {:task-queue task-queue})]

    (try
      ;; --- Example 1: Synchronous updates ---
      (log/info "")
      (log/info "=== Example 1: Synchronous Updates ===")

      (let [workflow (c/create-workflow client counter-workflow
                                        {:task-queue task-queue
                                         :workflow-id "counter-sync"})]

        (c/start workflow {:initial-value 0})
        (Thread/sleep 500)

        ;; Send synchronous updates - these block until the update completes
        (log/info "Incrementing by 5...")
        (let [result (c/update workflow :increment {:amount 5})]
          (log/info "Counter after increment:" result))

        (log/info "Incrementing by 10...")
        (let [result (c/update workflow :increment {:amount 10})]
          (log/info "Counter after increment:" result))

        (log/info "Decrementing by 3...")
        (let [result (c/update workflow :decrement {:amount 3})]
          (log/info "Counter after decrement:" result))

        ;; Query-like update
        (log/info "Getting current value via update...")
        (let [result (c/update workflow :get-value {})]
          (log/info "Current value:" result))

        ;; Complete the workflow
        (c/>! workflow ::done {})
        (let [result @(c/get-result workflow)]
          (log/info "Final state:" result)))

      ;; --- Example 2: Async updates ---
      (log/info "")
      (log/info "=== Example 2: Async Updates ===")

      (let [workflow (c/create-workflow client counter-workflow
                                        {:task-queue task-queue
                                         :workflow-id "counter-async"})]

        (c/start workflow {:initial-value 100})
        (Thread/sleep 500)

        ;; Start an update without waiting for completion
        (log/info "Starting async update...")
        (let [handle (c/start-update workflow :increment {:amount 25})]
          (log/info "Update started with ID:" (:id handle))
          ;; Do other work here if needed...
          ;; Then wait for the result
          (let [result @(:result handle)]
            (log/info "Async update result:" result)))

        ;; Complete the workflow
        (c/>! workflow ::done {})
        @(c/get-result workflow))

      ;; --- Example 3: Validator rejection ---
      (log/info "")
      (log/info "=== Example 3: Validator Rejection ===")

      (let [workflow (c/create-workflow client counter-workflow
                                        {:task-queue task-queue
                                         :workflow-id "counter-validation"})]

        (c/start workflow {:initial-value 0})
        (Thread/sleep 500)

        ;; Try an invalid update - negative amount
        (log/info "Attempting invalid update (negative amount)...")
        (try
          (c/update workflow :increment {:amount -5})
          (log/error "Should have thrown!")
          (catch Exception e
            (log/info "Update rejected as expected:" (.getMessage e))))

        ;; Try an invalid update - non-numeric amount
        (log/info "Attempting invalid update (non-numeric)...")
        (try
          (c/update workflow :increment {:amount "ten"})
          (log/error "Should have thrown!")
          (catch Exception e
            (log/info "Update rejected as expected:" (.getMessage e))))

        ;; Complete the workflow
        (c/>! workflow ::done {})
        @(c/get-result workflow))

      ;; --- Example 4: Update-with-start ---
      (log/info "")
      (log/info "=== Example 4: Update-with-Start ===")

      (let [workflow (c/create-workflow client counter-workflow
                                        {:task-queue task-queue
                                         :workflow-id "counter-uws"
                                         :workflow-id-conflict-policy :use-existing})]

        ;; This starts the workflow AND sends the first update atomically
        (log/info "Starting workflow with initial update...")
        (let [handle (c/update-with-start workflow :increment {:amount 50}
                                          {:initial-value 1000})]
          (log/info "Update-with-start result:" @(:result handle)))

        ;; Subsequent updates go to the same workflow
        (let [result (c/update workflow :get-value {})]
          (log/info "Current value:" result))

        ;; Complete the workflow
        (c/>! workflow ::done {})
        (let [result @(c/get-result workflow)]
          (log/info "Final state:" result)))

      (log/info "")
      (log/info "=== All examples completed ===")

      (finally
        (worker/stop w)))

    (System/exit 0)))
