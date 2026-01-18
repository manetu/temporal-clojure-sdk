(ns temporal.sample.hello-world.main
  "Entry point for the Hello World sample.

  Prerequisites:
  - A running Temporal server (e.g., `temporal server start-dev`)

  Run with: lein run"
  (:require [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.sample.hello-world.core :refer [hello-workflow]]
            [environ.core :refer [env]])
  (:gen-class))

(def task-queue "hello-world-queue")

(defn -main
  "Starts a worker, executes the hello workflow, and prints the result."
  [& args]
  (log/info "Starting Hello World sample...")

  ;; Create a client connected to the Temporal server
  (let [client (c/create-client {:target (env :temporal-target "127.0.0.1:7233")
                                 :namespace (env :temporal-namespace "default")})

        ;; Start a worker to process workflows and activities on our task queue
        w (worker/start client {:task-queue task-queue})]

    (try
      ;; Create and start the workflow
      (let [workflow (c/create-workflow client hello-workflow {:task-queue task-queue})
            name (or (first args) "World")]

        (log/info "Starting workflow with name:" name)
        (c/start workflow {:name name})

        ;; Wait for the result
        (let [result @(c/get-result workflow)]
          (log/info "===================================")
          (log/info "Workflow result:" result)
          (log/info "===================================")))

      (finally
        ;; Clean up
        (worker/stop w)))

    (log/info "Sample completed.")
    (System/exit 0)))
