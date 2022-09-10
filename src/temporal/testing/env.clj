;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.testing.env
  "Methods and utilities to assist with unit-testing Temporal workflows"
  (:require [temporal.client.worker :as worker]
            [temporal.internal.utils :as u])
  (:import [io.temporal.testing TestWorkflowEnvironment]))

(defn create
  "
Creates a mock environment and an associated client connection for emulating the combination of a worker and a Temporal
backend, suitable for unit testing.

Arguments:

- `queue-name`: The name of the task-queue for mock worker instance to listen on.  Accepts a string or fully-qualified
                keyword.
- `ctx`:        (optional) an opaque handle that is passed as the first argument of [[temporal.workflow/defworkflow]]
                and [[temporal.activity/defactivity]].  Useful for passing state such as database or network
                connections.  Not interpreted in any manner.

```clojure
(let [{:keys [client] :as instance} (create ::my-queue {:some \"context\"}]
  ;; create and invoke workflows
  (stop instance))
```
"
  ([queue-name] (create queue-name nil))
  ([queue-name ctx]
   (let [env (TestWorkflowEnvironment/newInstance)
         worker (.newWorker env (u/namify queue-name))]
     (worker/init worker ctx)
     (.start env)
     {:env env :worker worker :client (.getWorkflowClient env)})))

(defn stop
  "
Stops the test environment created by [[create]].  Does not wait for shutdown to complete.  For coordinated shutdown,
see [[synchronized-stop]].

```clojure
(stop instance)
```
"
  [{:keys [^TestWorkflowEnvironment env]}]
  (.shutdown env))

(defn synchronized-stop
  "
Stops the test environment created by [[create]].  Blocks until the environment has shut down.  For async termination,
see [[stop]]

```clojure
(synchronized-stop instance)
```
"
  [{:keys [^TestWorkflowEnvironment env]}]
  (.close env))