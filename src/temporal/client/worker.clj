;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.client.worker
  "Methods for managing a Temporal worker instance"
  (:require [taoensso.timbre :as log]
            [temporal.internal.activity :as a]
            [temporal.internal.workflow :as w]
            [temporal.internal.utils :as u])
  (:import [io.temporal.worker Worker WorkerFactory WorkerOptions WorkerOptions$Builder]
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

| Value                                          | Mandatory   | Description                                                       | Type             | Default |
| ------------                                   | ----------- | ----------------------------------------------------------------- | ---------------- | ------- |
| :task-queue                                    | y           | The name of the task-queue for this worker instance to listen on. | String / keyword |         |
| :ctx                                           |             | An opaque handle that is passed back as the first argument of [[temporal.workflow/defworkflow]] and [[temporal.activity/defactivity]], useful for passing state such as database or network connections.  | <any> | nil |
| :dispatch                                      |             | An optional map explicitly setting the dispatch table             | See below        | All visible activities/workers are automatically registered |
| :max-concurrent-activity-task-pollers          |             | Number of simultaneous poll requests on activity task queue. Consider incrementing if the worker is not throttled due to `MaxActivitiesPerSecond` or `MaxConcurrentActivityExecutionSize` options and still cannot keep up with the request rate. | int | 5 |
| :max-concurrent-activity-execution-size        |             | Maximum number of activities executed in parallel.                | int              | 200     |
| :max-concurrent-local-activity-execution-size  |             | Maximum number of local activities executed in parallel.          | int              | 200     |
| :max-concurrent-workflow-task-pollers          |             | Number of simultaneous poll requests on workflow task queue.      | int              | 2       |
| :max-concurrent-workflow-task-execution-size   |             | Maximum number of simultaneously executed workflow tasks.         | int              | 200     |
| :default-deadlock-detection-timeout            |             | Time period in ms that will be used to detect workflow deadlock.  | long             | 1000    |
| :default-heartbeat-throttle-interval           |             | Default amount of time between sending each pending heartbeat.    | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 30s |
| :max-heartbeat-throttle-interval               |             | Maximum amount of time between sending each pending heartbeat.    | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 60s |
| :local-activity-worker-only                    |             | Worker should only handle workflow tasks and local activities.    | boolean          | false   |
| :max-taskqueue-activities-per-second           |             | Sets the rate limiting on number of activities per second.        | double           | 0.0 (unlimited) |
| :max-workers-activities-per-second             |             | Maximum number of activities started per second.                  | double           | 0.0 (unlimited) |

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
  {:max-concurrent-activity-task-pollers             #(.setMaxConcurrentActivityTaskPollers ^WorkerOptions$Builder %1 %2)
   :max-concurrent-activity-execution-size           #(.setMaxConcurrentActivityExecutionSize ^WorkerOptions$Builder %1 %2)
   :max-concurrent-local-activity-execution-size     #(.setMaxConcurrentLocalActivityExecutionSize ^WorkerOptions$Builder %1 %2)
   :max-concurrent-workflow-task-pollers             #(.setMaxConcurrentWorkflowTaskPollers ^WorkerOptions$Builder %1 %2)
   :max-concurrent-workflow-task-execution-size      #(.setMaxConcurrentWorkflowTaskExecutionSize ^WorkerOptions$Builder %1 %2)
   :default-deadlock-detection-timeout               #(.setDefaultDeadlockDetectionTimeout ^WorkerOptions$Builder %1 %2)
   :default-heartbeat-throttle-interval              #(.setDefaultHeartbeatThrottleInterval ^WorkerOptions$Builder %1 %2)
   :max-heartbeat-throttle-interval                  #(.setMaxHeartbeatThrottleInterval ^WorkerOptions$Builder %1 %2)
   :local-activity-worker-only                       #(.setLocalActivityWorkerOnly ^WorkerOptions$Builder %1 %2)
   :max-taskqueue-activities-per-second              #(.setMaxTaskQueueActivitiesPerSecond ^WorkerOptions$Builder %1 %2)
   :max-workers-activities-per-second                #(.setMaxWorkerActivitiesPerSecond ^WorkerOptions$Builder %1 %2)})

(defn ^:no-doc worker-options->
  ^WorkerOptions [params]
  (u/build (WorkerOptions/newBuilder (WorkerOptions/getDefaultInstance)) worker-options params))

(defn start
  "
Starts a worker processing loop.

Arguments:

- `client`:     WorkflowClient instance returned from [[temporal.client.core/create-client]]
- `options`:    Worker start options (See [[worker-options]])

```clojure
(start {:task-queue ::my-queue :ctx {:some \"context\"}})
```
"
  [client {:keys [task-queue] :as options}]
  (let [factory (WorkerFactory/newInstance client)
        worker  (.newWorker factory (u/namify task-queue) (worker-options-> options))]
    (init worker options)
    (.start factory)
    {:factory factory :worker worker}))

(defn stop
  "
Stops a running worker.

Arguments:

- `instance`: Result returned from original call to ([[start]])

```clojure
(let [instance (start {:task-queue ::my-queue})]
   ...
   (stop instance))
```
"
  [{:keys [^WorkerFactory factory] :as instance}]
  (.shutdown factory))
