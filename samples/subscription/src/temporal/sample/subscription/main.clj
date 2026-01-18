(ns temporal.sample.subscription.main
  "Entry point for the Subscription sample.

  Prerequisites:
  - A running Temporal server (e.g., `temporal server start-dev`)

  Run with: lein run"
  (:require [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.sample.subscription.core :refer [subscription-workflow]]
            [environ.core :refer [env]])
  (:gen-class))

(def task-queue "subscription-queue")

(defn -main
  "Demonstrates a long-running subscription workflow."
  [& _args]
  (log/info "Starting Subscription sample...")
  (log/info "")
  (log/info "This sample runs a subscription workflow that:")
  (log/info "  1. Polls for updates every 2 seconds")
  (log/info "  2. Processes any updates found")
  (log/info "  3. Uses continue-as-new every 5 iterations")
  (log/info "  4. Can be cancelled gracefully via signal")
  (log/info "")
  (log/info "The workflow will run for ~15 seconds (5+ iterations)")
  (log/info "then receive a cancel signal.")
  (log/info "")

  (let [client (c/create-client {:target (env :temporal-target "127.0.0.1:7233")
                                 :namespace (env :temporal-namespace "default")})
        w (worker/start client {:task-queue task-queue})]

    (try
      (let [workflow (c/create-workflow client subscription-workflow
                                        {:task-queue task-queue
                                         :workflow-id "subscription-demo"})]

        ;; Start the subscription workflow
        (log/info "Starting subscription workflow...")
        (c/start workflow {:subscription-id "SUB-12345"})

        ;; Let it run for a while (will process several iterations)
        ;; The workflow will continue-as-new after 5 iterations automatically
        (log/info "")
        (log/info "Workflow running... (waiting 15 seconds)")
        (Thread/sleep 15000)

        ;; Send cancel signal for graceful shutdown
        (log/info "")
        (log/info "Sending cancel signal...")
        (c/>! workflow ::cancel true)

        ;; Wait for the result
        (let [result @(c/get-result workflow)]
          (log/info "")
          (log/info "=========================================")
          (log/info "Subscription workflow result:" result)
          (log/info "=========================================")
          (log/info "")
          (log/info "Note: The workflow likely continued-as-new multiple times.")
          (log/info "Check the Temporal UI to see the workflow execution chain.")))

      (finally
        (worker/stop w)))

    (log/info "Sample completed.")
    (System/exit 0)))
