;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.testing.env
  "Methods and utilities to assist with unit-testing Temporal workflows"
  (:require [temporal.client.worker :as worker]
            [temporal.internal.utils :as u])
  (:import [io.temporal.testing TestWorkflowEnvironment]))

(defn create
  "
Creates a mock Temporal backend, suitable for unit testing.

A worker may be created with [[start]] and a client may be connected with [[get-client]]
"
  []
  (TestWorkflowEnvironment/newInstance))

(defn start
  "
Starts a Temporal worker associated with the mock environment created with [[create]].

Arguments:

  - `options`:  See [[temporal.client.worker/worker-options]]

```clojure
(let [env (create)]
  (start env {:task-queue ::my-queue :ctx {:some \"context\"}})
  ;; create and invoke workflows
  (stop env))
```
"
  [env {:keys [task-queue] :as options}]
  (let [worker (.newWorker env (u/namify task-queue))]
    (worker/init worker options)
    (.start env)
    :ok))

(defn stop
  "
Stops the test environment created by [[create]].  Does not wait for shutdown to complete.  For coordinated shutdown,
see [[synchronized-stop]].

```clojure
(stop instance)
```
"
  [^TestWorkflowEnvironment env]
  (.shutdown env))

(defn synchronized-stop
  "
Stops the test environment created by [[create]].  Blocks until the environment has shut down.  For async termination,
see [[stop]]

```clojure
(synchronized-stop instance)
```
"
  [^TestWorkflowEnvironment env]
  (.close env))

(defn get-client
  "Returns a client instance associated with the mock environment created by [[create]]"
  [env]
  (.getWorkflowClient env))
