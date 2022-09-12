;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.client.worker
  "Methods for managing a Temporal worker instance"
  (:require [taoensso.timbre :as log]
            [temporal.internal.activity :as a]
            [temporal.internal.workflow :as w]
            [temporal.internal.utils :as u])
  (:import [io.temporal.worker Worker WorkerFactory]
           [temporal.internal.dispatcher DynamicWorkflowProxy]
           [io.temporal.workflow DynamicWorkflow]))

(defn ^:no-doc  init
  "
Initializes a worker instance, suitable for real connections or unit-testing with temporal.testing.env
"
  [^Worker worker {:keys [ctx] {:keys [activities workflows] :as dispatch} :dispatch}]
  (let [dispatch (if (nil? dispatch)
                   {:activities (a/auto-dispatch) :workflows (w/auto-dispatch)}
                   {:activities (a/import-dispatch activities) :workflows (w/import-dispatch workflows)})]
    (log/trace "init:" dispatch)
    (.registerActivitiesImplementations worker (to-array [(a/dispatcher ctx (:activities dispatch))]))
    (.addWorkflowImplementationFactory worker DynamicWorkflowProxy
                                       (u/->Func
                                        (fn []
                                          (new DynamicWorkflowProxy
                                               (reify DynamicWorkflow
                                                 (execute [_ args]
                                                   (w/execute ctx (:workflows dispatch) args)))))))))
(def worker-options
  "
Options for configuring workers (See [[start]])

| Value        | Mandatory   | Description                                                       | Type             | Default |
| ------------ | ----------- | ----------------------------------------------------------------- | ---------------- | ------- |
| :queue-name  | y           | The name of the task-queue for this worker instance to listen on. | String / keyword |         |
| :ctx         |             | An opaque handle that is passed back as the first argument of [[temporal.workflow/defworkflow]] and [[temporal.activity/defactivity]], useful for passing state such as database or network connections.  | <any> | nil |
| :dispatch    |             | An optional map explicitly setting the dispatch table             | See below        | All visible activities/workers are automatically registered |

#### dispatch-table

| Value        | Description                                                       |
| ------------ | ----------------------------------------------------------------- |
| :activities  | Vector of [[temporal.activity/defactivity]] symbols to register   |
| :workflows   | Vector of [[temporal.workflow/defworkflow]] symbols to register   |

```clojure
(defactivity my-activity ...)
(defworkflow my-workflow ...)

(let [worker-options {:dispatch {:activities [my-activity] :workflows [my-workflow]}}]
   ...)
```

"
  nil)

(defn start
  "
Starts a worker processing loop.

Arguments:

- `client`:     WorkflowClient instance returned from [[temporal.client.core/create-client]]
- `options`:    Worker start options (See [[worker-options]])

```clojure
(start {:queue-name ::my-queue :ctx {:some \"context\"}})
```
"
  [client {:keys [queue-name] :as options}]
  (let [factory (WorkerFactory/newInstance client)
        worker  (.newWorker factory (u/namify queue-name))]
    (init worker options)
    (.start factory)
    {:factory factory :worker worker}))

(defn stop
  "
Stops a running worker.

Arguments:

- `instance`: Result returned from original call to ([[start]])

```clojure
(let [instance (start {:queue-name ::my-queue})]
   ...
   (stop instance))
```
"
  [{:keys [^WorkerFactory factory] :as instance}]
  (.shutdown factory))
