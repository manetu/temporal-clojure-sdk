;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.client.worker
  "Methods for managing a Temporal worker instance"
  (:require [temporal.internal.activity :as a]
            [temporal.internal.utils :as u])
  (:import [io.temporal.worker Worker WorkerFactory]
           [temporal.internal.dispatcher WorkflowImpl]))

(defn ^:no-doc  init
  "
Initializes a worker instance, suitable for real connections or unit-testing with temporal.testing.env
"
  [^Worker worker ctx]
  (.addWorkflowImplementationFactory worker WorkflowImpl (u/->Func (fn [] (new WorkflowImpl ctx))))
  (.registerActivitiesImplementations worker (to-array [(a/dispatcher ctx)])))

(defn start
  "
Starts a worker processing loop.

Arguments:

- `client`:     WorkflowClient instance returned from [[temporal.client.core/create-client]]
- `queue-name`: The name of the task-queue for this worker instance to listen on.  Accepts a string or fully-qualified
                keyword.
- `ctx`:        (optional) an opaque handle that is passed as the first argument of [[temporal.workflow/defworkflow]]
                and [[temporal.activity/defactivity]].  Useful for passing state such as database or network
                connections.  Not interpreted in any manner.

```clojure
(start ::my-queue {:some \"context\"})
```
"
  ([client queue-name] (start client queue-name nil))
  ([client queue-name ctx]
   (let [factory (WorkerFactory/newInstance client)
         worker  (.newWorker factory (u/namify queue-name))]
     (init worker ctx)
     (.start factory)
     {:factory factory :worker worker})))

(defn stop
  "
Stops a running worker.

Arguments:

- `instance`: Result returned from original call to ([[start]])

```clojure
(let [instance (start ::my-queue)]
   ...
   (stop instance))
```
"
  [{:keys [^WorkerFactory factory] :as instance}]
  (.shutdown factory))
