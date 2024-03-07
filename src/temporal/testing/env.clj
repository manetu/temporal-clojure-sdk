;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.testing.env
  "Methods and utilities to assist with unit-testing Temporal workflows"
  (:require [temporal.client.worker :as worker]
            [temporal.client.core :as client]
            [temporal.internal.utils :as u])
  (:import [io.temporal.testing TestWorkflowEnvironment TestEnvironmentOptions TestEnvironmentOptions$Builder]))

(def ^:no-doc test-env-options
  {:worker-factory-options         #(.setWorkerFactoryOptions ^TestEnvironmentOptions$Builder %1 (worker/worker-factory-options-> %2))
   :workflow-client-options        #(.setWorkflowClientOptions ^TestEnvironmentOptions$Builder %1 (client/client-options-> %2))
   :workflow-service-stub-options  #(.setWorkflowServiceStubsOptions ^TestEnvironmentOptions$Builder %1 (client/stub-options-> %2))
   :metrics-scope                  #(.setMetricsScope ^TestEnvironmentOptions$Builder %1 %2)})

(defn ^:no-doc test-env-options->
  ^TestEnvironmentOptions [params]
  (u/build (TestEnvironmentOptions/newBuilder) test-env-options params))

(defn create
  "
Creates a mock Temporal backend, suitable for unit testing.

A worker may be created with [[start]] and a client may be connected with [[get-client]]

Arguments:

- `options`: Client configuration option map (See below)

#### options map

| Value                           | Description                                   | Type         | Default |
| -------------------------       | --------------------------------------------- | ------------ | ------- |
| :worker-factory-options         |                                               | [[worker/worker-factory-options]] | |
| :workflow-client-options        |                                               | [[client/client-options]] | |
| :workflow-service-stub-options  |                                               | [[client/stub-options]] | |
| :metrics-scope                  | The scope to be used for metrics reporting    | [Scope](https://github.com/uber-java/tally/blob/master/core/src/main/java/com/uber/m3/tally/Scope.java) | |


"
  ([]
   (TestWorkflowEnvironment/newInstance))
  ([options]
   (TestWorkflowEnvironment/newInstance (test-env-options-> options))))

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
    worker))

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
