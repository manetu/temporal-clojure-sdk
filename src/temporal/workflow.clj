;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.workflow
  "Methods for defining and implementing Temporal workflows"
  (:require
   [taoensso.nippy :as nippy]
   [taoensso.timbre :as log]
   [promesa.core :as p]
   [temporal.common :as common]
   [temporal.internal.exceptions :as e]
   [temporal.internal.search-attributes :as sa]
   [temporal.internal.utils :as u]
   [temporal.internal.workflow :as w]
   [temporal.internal.child-workflow :as cw])
  (:import [io.temporal.api.common.v1 WorkflowExecution]
           [io.temporal.workflow ContinueAsNewOptions ContinueAsNewOptions$Builder DynamicQueryHandler DynamicUpdateHandler ExternalWorkflowStub Workflow WorkflowLock]
           [java.util.function Supplier]
           [java.time Duration]))

(defn get-info
  "Return info about the current workflow"
  []
  (w/get-info))

(defn- ->supplier
  ^Supplier [f]
  (reify Supplier
    (get [_]
      (f))))

(defn await
  "Efficiently parks the workflow until 'pred' evaluates to true.  Re-evaluates on each state transition"
  ([pred]
   (Workflow/await (->supplier pred)))
  ([^Duration duration pred]
   (Workflow/await duration (->supplier pred))))

(defn sleep
  "Efficiently parks the workflow for 'duration'"
  [^Duration duration]
  (Workflow/sleep duration))

(def default-version (int Workflow/DEFAULT_VERSION))

(defn get-version
  "Used to safely perform backwards incompatible changes to workflow definitions"
  [change-id min max]
  (Workflow/getVersion (u/namify change-id) min max))

(defn register-query-handler!
  "
Registers a DynamicQueryHandler listener that handles queries sent to the workflow, using [[temporal.client.core/query]].

Use inside a workflow definition with 'f' closing over the workflow state (e.g. atom) and
evaluating to results in function of the workflow state and its 'query-type' and 'args' arguments.

Arguments:
- `f`: a 2-arity function, expecting 2 arguments, evaluating to something serializable.

`f` arguments:
- `query-type`: keyword
- `args`: params value or data structure

```clojure
(defworkflow stateful-workflow
  [{:keys [init] :as args}]
  (let [state (atom init)]
    (register-query-handler! (fn [query-type args]
                               (when (= query-type :my-query)
                                 (get-in @state [:path :to :answer]))))
    ;; workflow implementation
    ))
```
"
  [f]
  (Workflow/registerListener
   (reify DynamicQueryHandler
     (handle [_ query-type args]
       (let [query-type (keyword query-type)
             args (u/->args args)]
         (log/trace "handling query->" "query-type:" query-type "args:" args)
         (-> (f query-type args)
             (nippy/freeze)))))))

(defn register-update-handler!
  "
Registers a DynamicUpdateHandler listener that handles updates sent to the workflow.

Updates are similar to queries but with important differences:
- Updates can mutate workflow state
- Updates can perform blocking operations (activities, child workflows, sleep, await)
- Updates return values to the caller
- Updates can have optional validators

Use inside a workflow definition with 'f' closing over the workflow state (e.g. atom).
The handler function should return a value that will be sent back to the update caller.

Arguments:
- `f`: a 2-arity function, expecting 2 arguments, evaluating to something serializable.

`f` arguments:
- `update-type`: keyword identifying the update type
- `args`: params value or data structure

#### Options:

When using the 2-arity version, you can provide an options map:

| Value      | Description                                              | Type     |
| ---------- | -------------------------------------------------------- | -------- |
| :validator | A function (fn [update-type args]) that validates inputs | Function |

The validator function is called before the update handler and should throw an exception
if the update request is invalid. Validators must not mutate state or perform blocking operations.

```clojure
(defworkflow stateful-workflow
  [{:keys [init] :as args}]
  (let [state (atom init)]
    (register-update-handler!
      (fn [update-type args]
        (when (= update-type :increment)
          (swap! state update :counter + (:amount args))
          @state))
      {:validator (fn [update-type args]
                    (when (and (= update-type :increment)
                               (neg? (:amount args)))
                      (throw (IllegalArgumentException. \"amount must be positive\"))))})
    ;; workflow implementation
    ))
```
"
  ([f] (register-update-handler! f {}))
  ([f {:keys [validator]}]
   (Workflow/registerListener
    (reify DynamicUpdateHandler
      (handleExecute [_ update-type args]
        (let [update-type (keyword update-type)
              args (u/->args args)]
          (log/trace "handling update->" "update-type:" update-type "args:" args)
          (-> (f update-type args)
              (nippy/freeze))))
      (handleValidate [_ update-type args]
        (when validator
          (let [update-type (keyword update-type)
                args (u/->args args)]
            (log/trace "validating update->" "update-type:" update-type "args:" args)
            (validator update-type args))))))))

(defmacro defworkflow
  "
Defines a new workflow, similar to defn, expecting a 1-arity parameter list and body.  Should evaluate to something
serializable, which will become available for [[temporal.client.core/get-result]].

Arguments:

- `args`: Passed from 'params' to [[temporal.client.core/start]] or [[temporal.client.core/signal-with-start]]

```clojure
(defworkflow my-workflow
    [{:keys [foo]}]
    ...)

(let [w (create-workflow client my-workflow {:task-queue ::my-task-queue})]
   (start w {:foo \"bar\"}))
```
"
  [name params* & body]
  (let [fqn (u/get-fq-classname name)
        sname (str name)]
    (if (= (count params*) 2)                               ;; legacy
      (do
        (println (str *file* ":" (:line (meta &form)) " WARN: (defworkflow " name ") with [ctx {:keys [signals args]}] is deprecated.  Use [args] form."))
        `(def ~name ^{::w/def {:name ~sname :fqn ~fqn :type :legacy}}
           (fn [ctx# args#]
             (log/trace (str ~fqn ": ") args#)
             (let [f# (fn ~params* (do ~@body))]
               (f# ctx# args#)))))
      (do
        `(def ~name ^{::w/def {:name ~sname :fqn ~fqn}}
           (fn [args#]
             (log/trace (str ~fqn ": ") args#)
             (let [f# (fn ~params* (do ~@body))]
               (f# args#))))))))

(defn invoke
  "
Invokes a 'child workflow' with 'params' from within a workflow context.
Returns a promise that when derefed will resolve to the evaluation of the defworkflow once the workflow concludes.

Arguments:

- `workflow`: A reference to a symbol registered with [[defworkflow]], called a Child Workflow usually.
- `params`: Opaque serializable data that will be passed as arguments to the invoked child workflow
- `options`: See below.

#### options map

| Value                       | Description                                                                                | Type         | Default |
| --------------------------- | ------------------------------------------------------------------------------------------ | ------------ | ------- |
| :task-queue                 | Task queue to use for child workflow tasks                                                 | String | |
| :workflow-id                | Workflow id to use when starting                                                           | String | |
| :workflow-id-reuse-policy   | Specifies server behavior if a completed workflow with the same id exists                  | See `workflow id reuse policy types` below | |
| :parent-close-policy        | Specifies how this workflow reacts to the death of the parent workflow                     | See `parent close policy types` below | |
| :workflow-execution-timeout | The time after which child workflow execution is automatically terminated                  | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :workflow-run-timeout       | The time after which child workflow run is automatically terminated                        | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :workflow-task-timeout      | Maximum execution time of a single workflow task                                           | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 10 seconds |
| :retry-options              | RetryOptions that define how child workflow is retried in case of failure                  | [[temporal.common/retry-options]] | |
| :cron-schedule              | A cron schedule string                                                                     | String | |
| :cancellation-type          | In case of a child workflow cancellation it fails with a CanceledFailure                   | See `cancellation types` below | |
| :memo                       | Specifies additional non-indexed information in result of list workflow                    | String | |

#### cancellation types

| Value                        | Description                                                                 |
| -------------------------    | --------------------------------------------------------------------------- |
| :try-cancel                  | Initiate a cancellation request and immediately report cancellation to the parent |
| :abandon                     | Do not request cancellation of the child workflow |
| :wait-cancellation-completed | Wait for child cancellation completion |
| :wait-cancellation-requested | Request cancellation of the child and wait for confirmation that the request was received |

#### parent close policy types

| Value                        | Description                                                                 |
| -------------------------    | --------------------------------------------------------------------------- |
| :abandon                     | Do not request cancellation of the child workflow |
| :request-cancel              | Request cancellation of the child and wait for confirmation that the request was received |
| :terminate                   | Terminate the child workflow |

#### workflow id reuse policy types

| Value                        | Description                                                                 |
| ---------------------------- | --------------------------------------------------------------------------- |
| :allow-duplicate             | Allow starting a child workflow execution using the same workflow id. |
| :allow-duplicate-failed-only | Allow starting a child workflow execution using the same workflow id, only when the last execution's final state is one of [terminated, cancelled, timed out, failed] |
| :reject-duplicate            | Do not permit re-use of the child workflow id for this workflow. |
| :terminate-if-running        | If a workflow is running using the same child workflow ID, terminate it and start a new one. If no running child workflow, then the behavior is the same as ALLOW_DUPLICATE |

```clojure
(defworkflow my-workflow
   [ctx {:keys [foo] :as args}]
   ...)

(invoke my-workflow {:foo \"bar\"} {:start-to-close-timeout (Duration/ofSeconds 3))
```
"
  ([workflow params] (invoke workflow params {}))
  ([workflow params options]
   (let [wf-name (w/get-annotated-name workflow)
         stub (Workflow/newUntypedChildWorkflowStub wf-name (cw/child-workflow-options-> options))]
     (log/trace "invoke:" workflow "with" params options)
     (-> (.executeAsync stub u/bytes-type (u/->objarray params))
         (p/then (partial u/complete-invoke workflow))
         (p/catch e/slingshot? e/recast-stone)
         (p/catch (fn [e]
                    (log/error e)
                    (throw e)))))))

(def ^:no-doc continue-as-new-option-spec
  {:task-queue            #(.setTaskQueue ^ContinueAsNewOptions$Builder %1 (u/namify %2))
   :workflow-run-timeout  #(.setWorkflowRunTimeout ^ContinueAsNewOptions$Builder %1 %2)
   :workflow-task-timeout #(.setWorkflowTaskTimeout ^ContinueAsNewOptions$Builder %1 %2)
   :retry-options         #(.setRetryOptions ^ContinueAsNewOptions$Builder %1 (common/retry-options-> %2))
   :memo                  #(.setMemo ^ContinueAsNewOptions$Builder %1 %2)
   :search-attributes     #(.setSearchAttributes ^ContinueAsNewOptions$Builder %1 %2)})

(defn ^:no-doc continue-as-new-options->
  ^ContinueAsNewOptions [options]
  (u/build (ContinueAsNewOptions/newBuilder) continue-as-new-option-spec options))

(defn continue-as-new
  "
Continues the current workflow execution as a new execution with the same workflow ID.

This is useful for long-running workflows that accumulate large event histories. By calling
continue-as-new, the workflow completes and immediately restarts with a fresh event history,
passing 'params' as the new workflow arguments.

This function never returns - it always throws a ContinueAsNewError that terminates
the current workflow run.

Arguments:
- `params`: Arguments to pass to the new workflow run
- `options`: (optional) Options map to customize the new run

#### options map

| Value                   | Description                                                          | Type         | Default |
| ----------------------- | -------------------------------------------------------------------- | ------------ | ------- |
| :task-queue             | Task queue for the new run                                           | String       | same as current |
| :workflow-run-timeout   | Timeout for the new workflow run                                     | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :workflow-task-timeout  | Timeout for individual workflow tasks                                | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :retry-options          | Retry options for the new run                                        | [[temporal.common/retry-options]] | |
| :memo                   | Memo fields for the new run                                          | Map          | |
| :search-attributes      | Search attributes for the new run                                    | Map          | |

```clojure
(defworkflow long-running-workflow
  [{:keys [iteration data]}]
  ;; Process data...
  (when (should-continue? data)
    (continue-as-new {:iteration (inc iteration) :data (next-batch data)})))
```
"
  ([params] (continue-as-new params {}))
  ([params options]
   (log/trace "continue-as-new:" params options)
   (Workflow/continueAsNew (continue-as-new-options-> options) (u/->objarray params))))

(defn upsert-search-attributes
  "
Updates search attributes for the current workflow execution.

Search attributes are indexed metadata that can be used to filter and search for workflows
in the Temporal UI and via the Visibility API. Unlike memo, search attributes are indexed
and can be used in list workflow queries.

The attributes map should have string keys (attribute names) and values that are maps
with :type and :value keys. To unset an attribute, pass nil as the value.

Arguments:
- `attrs`: A map of {attribute-name {:type type :value value}} where type is one of:
  - :text - Full-text searchable string
  - :keyword - Exact-match string
  - :int - 64-bit integer (Long)
  - :double - 64-bit floating point
  - :bool - Boolean
  - :datetime - OffsetDateTime
  - :keyword-list - List of exact-match strings

To unset an attribute, use {:type type :value nil}

Note: Search attributes must be pre-registered with the Temporal server before use.
In test environments, use the :search-attributes option when creating the test environment.

```clojure
(defworkflow order-workflow
  [{:keys [order-id status]}]
  ;; Update status as workflow progresses
  (upsert-search-attributes {\"OrderStatus\" {:type :keyword :value \"processing\"}})
  ;; ... do work ...
  (upsert-search-attributes {\"OrderStatus\" {:type :keyword :value \"completed\"}
                             \"CompletedAt\" {:type :datetime :value (java.time.OffsetDateTime/now)}}))
```
"
  [attrs]
  (log/trace "upsert-search-attributes:" attrs)
  (Workflow/upsertTypedSearchAttributes (sa/search-attribute-updates-> attrs)))

(defn new-lock
  "
Creates a new WorkflowLock for coordinating concurrent handler execution.

Temporal workflows execute on a single thread, so you don't need to worry about
parallelism. However, you DO need to worry about concurrency when signal or update
handlers can block (call activities, sleep, await, or child workflows).

**When you DON'T need this:**

Non-blocking handlers run to completion atomically. No lock needed:

```clojure
;; Non-blocking handler - runs atomically, no lock needed
(fn [update-type {:keys [amount]}]
  (swap! state update :counter + amount)
  @state)
```

**When you DO need this:**

Use a lock when your handler performs blocking operations AND accesses shared state.
At await points, other handlers can run and interleave:

```clojure
;; PROBLEM: Handler could be interrupted at the activity call
(fn [update-type args]
  (let [current (:counter @state)]                       ;; read state
    (let [result @(a/invoke validate-activity current)]  ;; BLOCKS - other handlers can run!
      (swap! state assoc :validated result))))           ;; another handler may have changed state
```

**Solution with lock:**

```clojure
(defworkflow my-workflow
  [args]
  (let [state (atom {})
        lock (new-lock)]
    (register-update-handler!
      (fn [update-type args]
        (with-lock lock
          ;; Lock ensures no other handler runs during this sequence
          (let [current (:counter @state)]
            (let [result @(a/invoke validate-activity current)]
              (swap! state assoc :validated result)
              @state)))))
    ;; workflow implementation
    ))
```
"
  ^WorkflowLock []
  (Workflow/newWorkflowLock))

(defmacro with-lock
  "
Executes body while holding the given WorkflowLock.

Ensures that the lock is released even if an exception is thrown.
Use this to protect handlers that perform blocking operations (activities, sleep,
await, child workflows) and access shared workflow state.

Non-blocking handlers don't need this - they run to completion atomically.

```clojure
(let [lock (new-lock)]
  (with-lock lock
    ;; No other handler can interleave during this sequence
    (let [current (:value @state)]
      (let [result @(a/invoke process-activity current)]
        (swap! state assoc :processed result)))))
```
"
  [lock & body]
  `(let [lock# ~lock]
     (.lock lock#)
     (try
       ~@body
       (finally
         (.unlock lock#)))))

(defn every-handler-finished?
  "
Returns true if all signal and update handlers have completed.

Use this to prevent your workflow from completing while handlers are still processing.
When handlers perform blocking operations (activities, child workflows, sleep, await),
the workflow could otherwise return before handlers finish their work, causing:
- Interrupted handler execution
- Client errors when retrieving update results
- Lost work

**Recommended usage:**

Wait for all handlers to complete before returning from your workflow:

```clojure
(defworkflow my-workflow
  [args]
  (let [state (atom {:counter 0})]
    (register-update-handler!
      (fn [update-type args]
        ;; Handler that does async work
        (let [result @(a/invoke process-activity args)]
          (swap! state assoc :processed result)
          @state)))
    ;; Do main workflow logic...
    (do-work state)
    ;; Wait for any in-progress handlers before completing
    (await every-handler-finished?)
    @state))
```

By default, your worker will log a warning when a workflow completes with unfinished
handlers. Using this function prevents those warnings and ensures handlers complete
their work.
"
  []
  (Workflow/isEveryHandlerFinished))

(defn external-workflow
  "
Creates a handle to an external workflow for signaling or canceling from within a workflow.

Use this function when you need to interact with another workflow that is running
independently (not a child workflow). This allows you to send signals or request
cancellation of the external workflow.

Note: External workflow stubs do NOT support query, update, or getResult - these
are client-only operations. Use the client API for those operations.

Arguments:
- `workflow-id`: The ID of the external workflow
- `run-id`: (optional) The specific run ID to target. If not provided, targets the
  latest run of the workflow.

Returns a map containing:
- `:stub` - The underlying ExternalWorkflowStub
- `:workflow-id` - The workflow ID
- `:run-id` - The run ID (if provided)

```clojure
(defworkflow coordinator-workflow
  [{:keys [worker-workflow-id]}]
  ;; Create handle to external workflow
  (let [worker (external-workflow worker-workflow-id)]
    ;; Send signal to external workflow
    (signal-external worker :status-update {:progress 50})
    ;; Or cancel it if needed
    (cancel-external worker)))
```
"
  ([workflow-id]
   {:stub        (Workflow/newUntypedExternalWorkflowStub ^String workflow-id)
    :workflow-id workflow-id})
  ([workflow-id run-id]
   (let [execution (-> (WorkflowExecution/newBuilder)
                       (.setWorkflowId workflow-id)
                       (.setRunId run-id)
                       (.build))]
     {:stub        (Workflow/newUntypedExternalWorkflowStub execution)
      :workflow-id workflow-id
      :run-id      run-id})))

(defn cancel-external
  "
Requests cancellation of an external workflow from within the current workflow.

This sends a cancellation request to the external workflow. The external workflow
will receive a CancellationException and can handle it appropriately.

Arguments:
- `external-wf`: An external workflow handle created by [[external-workflow]]

```clojure
(defworkflow supervisor-workflow
  [{:keys [worker-id]}]
  (let [worker (external-workflow worker-id)]
    ;; Cancel the worker workflow
    (cancel-external worker)))
```
"
  [external-wf]
  (let [^ExternalWorkflowStub stub (:stub external-wf)]
    (log/trace "cancel-external:" (:workflow-id external-wf))
    (.cancel stub)))

(defn signal-external
  "
Sends a signal to an external workflow from within the current workflow.

This is an alternative to [[temporal.signals/>!]] that works with external workflow
handles created by [[external-workflow]]. The external workflow must have a signal
handler registered for the given signal name.

Arguments:
- `external-wf`: An external workflow handle created by [[external-workflow]]
- `signal-name`: The signal name (keyword or string)
- `params`: The signal payload (must be serializable)

```clojure
(defworkflow coordinator-workflow
  [{:keys [worker-ids]}]
  (doseq [worker-id worker-ids]
    (let [worker (external-workflow worker-id)]
      ;; Send signal to each worker
      (signal-external worker :new-task {:task-id (random-uuid)}))))
```
"
  [external-wf signal-name params]
  (let [^ExternalWorkflowStub stub (:stub external-wf)
        signal-name (u/namify signal-name)]
    (log/trace "signal-external:" (:workflow-id external-wf) signal-name params)
    (.signal stub signal-name (u/->objarray params))))

(defn get-execution
  "
Returns the workflow execution information for an external workflow handle.

This provides access to the workflow ID and run ID of the external workflow.

Arguments:
- `external-wf`: An external workflow handle created by [[external-workflow]]

Returns a map containing:
- `:workflow-id` - The workflow ID
- `:run-id` - The run ID

```clojure
(defworkflow parent-workflow
  [{:keys [child-id]}]
  (let [child (external-workflow child-id)
        execution (get-execution child)]
    (log/info \"External workflow:\" (:workflow-id execution) (:run-id execution))))
```
"
  [external-wf]
  (let [^ExternalWorkflowStub stub (:stub external-wf)
        execution (.getExecution stub)]
    {:workflow-id (.getWorkflowId execution)
     :run-id      (.getRunId execution)}))
