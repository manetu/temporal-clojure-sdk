(ns temporal.sample.hello-world.core
  "A simple Hello World example demonstrating basic Temporal workflow and activity usage.

  This sample shows:
  - Defining an activity with defactivity
  - Defining a workflow with defworkflow
  - Invoking an activity from a workflow
  - Starting a workflow and getting its result"
  (:require [taoensso.timbre :as log]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a])
  (:import [java.time Duration]))

;; -----------------------------------------------------------------------------
;; Activities
;; -----------------------------------------------------------------------------

(defactivity greet-activity
  "An activity that creates a greeting message.

  Activities are where you perform side effects like calling external services,
  accessing databases, or any non-deterministic operations."
  [ctx {:keys [name]}]
  (log/info "Creating greeting for:" name)
  ;; Simulate some work (e.g., calling an external service)
  (str "Hello, " name "!"))

;; -----------------------------------------------------------------------------
;; Workflows
;; -----------------------------------------------------------------------------

(defworkflow hello-workflow
  "A simple workflow that greets a person by name.

  Workflows orchestrate activities and must be deterministic. All side effects
  should happen in activities, not directly in the workflow."
  [{:keys [name]}]
  (log/info "Starting hello-workflow for:" name)

  ;; Invoke the activity and wait for the result
  ;; The @ dereferences the promise returned by invoke
  (let [greeting @(a/invoke greet-activity
                            {:name name}
                            {:start-to-close-timeout (Duration/ofSeconds 10)})]
    (log/info "Workflow completed with greeting:" greeting)
    greeting))
