;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.client.core
  "Methods for client interaction with Temporal"
  (:require [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [promesa.core :as p]
            [temporal.client.options :as copts]
            [temporal.internal.workflow :as w]
            [temporal.internal.utils :as u]
            [temporal.internal.exceptions :as e])
  (:import [java.time Duration]
           [io.temporal.client WorkflowClient WorkflowStub WorkflowUpdateHandle WorkflowUpdateStage UpdateOptions]))

(defn- ^:no-doc create-client*
  [service-stub options]
  (WorkflowClient/newInstance service-stub (copts/workflow-client-options-> options)))

(defn create-client-async
  "
Creates a new client instance suitable for implementing Temporal workers (See [[temporal.client.worker/start]]) or
workflow clients (See [[create-workflow]]).  Will not verify the connection before returning.

Arguments:

- `options`: Options for configuring the `WorkflowClient` (See [[temporal.client.options/workflow-client-options]] and [[temporal.client.options/stub-options]])

"
  ([] (create-client-async {}))
  ([options]
   (create-client* (copts/service-stub-> options) options)))

(defn create-client
  "
Creates a new client instance suitable for implementing Temporal workers (See [[temporal.client.worker/start]]) or
workflow clients (See [[create-workflow]]).  The caller is blocked while the connection is verified for up to the
specified timeout duration, or 5s if not specified.

Arguments:

- `options`: Options for configuring the `WorkflowClient` (See [[temporal.client.options/workflow-client-options]] and [[temporal.client.options/stub-options]])
- `timeout`: Optional connection timeout as a [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) (Default: 5s).

"
  ([] (create-client {}))
  ([options]
   (create-client options (Duration/ofSeconds 5)))
  ([options timeout]
   (create-client* (copts/service-stub-> options timeout) options)))

(defn create-workflow
  "
Create a new workflow-stub instance, suitable for managing and interacting with a workflow through it's lifecycle.

*N.B.: The workflow will remain in an uninitialized and idle state until explicitly started with either ([[start]]),
([[signal-with-start]]), or ([[update-with-start]]).*

Arguments:

- `client`: A workflow client created with [[create-client]]
- `workflow`: The workflow definition (defworkflow)
- `options`: A map of workflow options:

| Option                         | Description                                                |
|--------------------------------|------------------------------------------------------------|
| `:task-queue`                  | (required) Task queue for the workflow                     |
| `:workflow-id`                 | Unique workflow identifier (auto-generated if not provided)|
| `:workflow-id-reuse-policy`    | Policy for reusing IDs of completed workflows              |
| `:workflow-id-conflict-policy` | Policy when workflow ID is already running (see below)     |
| `:workflow-execution-timeout`  | Total workflow execution timeout (Duration)                |
| `:workflow-run-timeout`        | Single workflow run timeout (Duration)                     |
| `:workflow-task-timeout`       | Workflow task processing timeout (Duration)                |
| `:retry-options`               | Retry configuration map                                    |
| `:cron-schedule`               | Cron expression for scheduled execution                    |
| `:memo`                        | Arbitrary metadata map                                     |
| `:search-attributes`           | Indexed attributes for workflow visibility                 |
| `:start-delay`                 | Delay before starting the workflow (Duration)              |

Workflow ID Conflict Policies (`:workflow-id-conflict-policy`):

| Policy               | Description                                          |
|----------------------|------------------------------------------------------|
| `:fail`              | Fail if workflow is already running (default)        |
| `:use-existing`      | Use the existing running workflow                    |
| `:terminate-existing`| Terminate existing workflow and start new one        |

```clojure
(let [w (create-workflow client my-workflow {:task-queue ::my-task-queue})]
  ;; do something with the instance 'w')
```
  "
  ([^WorkflowClient client workflow-id]
   (let [stub    (.newUntypedWorkflowStub client (u/namify workflow-id))]
     (log/trace "create-workflow id:" workflow-id)
     {:client client :stub stub}))
  ([^WorkflowClient client workflow options]
   (let [wf-name (w/get-annotated-name workflow)
         options (w/wf-options-> options)
         stub    (.newUntypedWorkflowStub client wf-name options)]
     (log/trace "create-workflow:" wf-name options)
     {:client client :stub stub})))

(defn start
  "
Starts 'worklow' with 'params'"
  [{:keys [^WorkflowStub stub] :as workflow} params]
  (log/trace "start:" params)
  (.start stub (u/->objarray params)))

(defn signal-with-start
  "
Signals 'workflow' with 'signal-params' on signal 'signal-name', starting it if not already running.

Arguments:

- `workflow`: workflow instance created with [[create-workflow]]
- `signal-name`: keyword (or coerceable) identifying the signal
- `signal-params`: data to send with the signal
- `wf-params`: arguments passed to the workflow if it needs to be started

By default, this will fail if the workflow is already running.  To signal an existing workflow instead,
set `:workflow-id-conflict-policy` to `:use-existing` when calling [[create-workflow]]:

```clojure
(let [w (create-workflow client my-workflow {:task-queue task-queue
                                              :workflow-id \"my-id\"
                                              :workflow-id-conflict-policy :use-existing})]
  (signal-with-start w :my-signal {:data \"value\"} {:initial \"args\"}))
```
"
  [{:keys [^WorkflowStub stub] :as workflow} signal-name signal-params wf-params]
  (log/trace "signal-with-start->" "signal:" signal-name signal-params "workflow-params:" wf-params)
  (.signalWithStart stub (u/namify signal-name) (u/->objarray signal-params) (u/->objarray wf-params)))

(defn >!
  "
Sends 'params' as a signal 'signal-name' to 'workflow'

```clojure
(>! workflow ::my-signal {:msg \"Hi\"})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} signal-name params]
  (log/trace ">!" signal-name params)
  (.signal stub (u/namify signal-name) (u/->objarray params)))

(defn get-result
  "
Retrieves the final result of 'workflow'.  Returns a promise that when derefed will resolve to the evaluation of the
defworkflow once the workflow concludes.

```clojure
(defworkflow my-workflow
  [ctx args]
  ...)

(let [w (create ...)]
   (start w ...)
   @(get-result w))
```
"
  [{:keys [^WorkflowStub stub] :as workflow}]
  (-> (.getResultAsync stub u/bytes-type)
      (p/then nippy/thaw)
      (p/catch e/slingshot? e/recast-stone)
      (p/catch (fn [e]
                 (log/error e)
                 (throw e)))))

(defn query
  "
Sends query with 'query-type' and 'args' to 'workflow', returns a value.
The query result is computed by a query-handler, registered inside the workflow definition
using [[temporal.workflow/register-query-handler!]].

Arguments:
- `query-type`: keyword (or coerceable into a keyword)
- `args`: serializable query params

```clojure
(query workflow ::my-query {:foo \"bar\"})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} query-type args]
  (-> (.query stub (u/namify query-type) u/bytes-type (u/->objarray args))
      (nippy/thaw)))

(defn update
  "
Sends an update with 'update-type' and 'args' to 'workflow', returns a value.
The update is processed by an update-handler, registered inside the workflow definition
using [[temporal.workflow/register-update-handler!]].

Unlike queries, updates can:
- Mutate workflow state
- Perform blocking operations (activities, child workflows, sleep, await)
- Return values to the caller

Arguments:
- `workflow`: workflow instance created with [[create-workflow]]
- `update-type`: keyword (or coerceable into a keyword) identifying the update type
- `args`: serializable update params

```clojure
(update workflow :increment {:amount 5})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} update-type args]
  (log/trace "update:" update-type args)
  (-> (.update stub (u/namify update-type) u/bytes-type (u/->objarray args))
      (nippy/thaw)))

(def ^:private stage->enum
  "Maps keyword stage to WorkflowUpdateStage enum"
  {:admitted  WorkflowUpdateStage/ADMITTED
   :accepted  WorkflowUpdateStage/ACCEPTED
   :completed WorkflowUpdateStage/COMPLETED})

(defn- wrap-update-handle
  "Wraps a WorkflowUpdateHandle to return a Clojure map with async result"
  [^WorkflowUpdateHandle handle]
  {:id (.getId handle)
   :execution (.getExecution handle)
   :result (-> (.getResultAsync handle)
               (p/then nippy/thaw)
               (p/catch e/slingshot? e/recast-stone)
               (p/catch (fn [e]
                          (log/error e)
                          (throw e))))})

(defn- build-update-options
  "Builds UpdateOptions from a Clojure map"
  [{:keys [update-name update-id wait-for-stage first-execution-run-id]}]
  (let [builder (UpdateOptions/newBuilder u/bytes-type)]
    (when update-name
      (.setUpdateName builder (u/namify update-name)))
    (when update-id
      (.setUpdateId builder update-id))
    (when wait-for-stage
      (.setWaitForStage builder (get stage->enum wait-for-stage)))
    (when first-execution-run-id
      (.setFirstExecutionRunId builder first-execution-run-id))
    (.build builder)))

(defn start-update
  "
Asynchronously sends an update with 'update-type' and 'args' to 'workflow'.
Returns an update handle that can be used to get the result later.

The update is processed by an update-handler, registered inside the workflow definition
using [[temporal.workflow/register-update-handler!]].

Arguments:
- `workflow`: workflow instance created with [[create-workflow]]
- `update-type`: keyword (or coerceable into a keyword) identifying the update type
- `args`: serializable update params
- `options`: (optional) map with:
  - `:wait-for-stage`: one of :admitted, :accepted, or :completed (default: :accepted)
  - `:update-id`: unique ID for idempotency (auto-generated if not provided)

Returns a map with:
- `:id`: the update ID
- `:execution`: the workflow execution
- `:result`: a promise that resolves to the update result

```clojure
(let [handle (start-update workflow :increment {:amount 5})]
  ;; Do other work...
  @(:result handle))
```
"
  ([workflow update-type args]
   (start-update workflow update-type args {}))
  ([{:keys [^WorkflowStub stub] :as workflow} update-type args {:keys [wait-for-stage update-id] :as options}]
   (log/trace "start-update:" update-type args options)
   (let [handle (if update-id
                  ;; Use UpdateOptions when custom update-id is specified
                  (let [update-options (build-update-options (merge {:update-name update-type
                                                                     :wait-for-stage (or wait-for-stage :accepted)}
                                                                    options))]
                    (.startUpdate stub update-options (u/->objarray args)))
                  ;; Use simple overload when no custom options needed
                  (let [stage (get stage->enum (or wait-for-stage :accepted))]
                    (.startUpdate stub (u/namify update-type) stage u/bytes-type (u/->objarray args))))]
     (wrap-update-handle handle))))

(defn get-update-handle
  "
Retrieves a handle to a previously initiated update request using its unique identifier.

Arguments:
- `workflow`: workflow instance created with [[create-workflow]]
- `update-id`: the unique ID of the update

Returns a map with:
- `:id`: the update ID
- `:execution`: the workflow execution
- `:result`: a promise that resolves to the update result

```clojure
(let [handle (get-update-handle workflow \"my-update-id\")]
  @(:result handle))
```
"
  [{:keys [^WorkflowStub stub] :as workflow} update-id]
  (log/trace "get-update-handle:" update-id)
  (let [handle (.getUpdateHandle stub update-id u/bytes-type)]
    (wrap-update-handle handle)))

(defn update-with-start
  "
Sends an update to a workflow, starting it if not already running.
This is useful for ensuring a workflow exists before sending an update.

Arguments:
- `workflow`: workflow instance created with [[create-workflow]]
- `update-type`: keyword (or coerceable into a keyword) identifying the update type
- `update-args`: serializable update params
- `start-args`: arguments to pass if the workflow needs to be started
- `options`: (optional) map with:
  - `:wait-for-stage`: one of :admitted, :accepted, or :completed (default: :accepted)
  - `:update-id`: unique ID for idempotency (auto-generated if not provided)

Returns a map with:
- `:id`: the update ID
- `:execution`: the workflow execution
- `:result`: a promise that resolves to the update result

```clojure
(let [handle (update-with-start workflow :increment {:amount 5} {:initial-value 0})]
  @(:result handle))
```
"
  ([workflow update-type update-args start-args]
   (update-with-start workflow update-type update-args start-args {}))
  ([{:keys [^WorkflowStub stub] :as workflow} update-type update-args start-args options]
   (log/trace "update-with-start:" update-type update-args start-args options)
   (let [update-options (build-update-options (merge {:update-name update-type
                                                      :wait-for-stage (or (:wait-for-stage options) :accepted)}
                                                     options))
         handle (.startUpdateWithStart stub update-options (u/->objarray update-args) (u/->objarray start-args))]
     (wrap-update-handle handle))))

(defn cancel
  "
Gracefully cancels 'workflow'

```clojure
(cancel workflow)
```
"
  [{:keys [^WorkflowStub stub] :as workflow}]
  (.cancel stub))

(defn terminate
  "
Forcefully terminates 'workflow'

```clojure
(terminate workflow \"unresponsive\", {})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} reason params]
  (.terminate stub reason (u/->objarray params)))
