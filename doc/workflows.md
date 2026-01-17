# Workflows

## What is a Workflow?

Workflows are resilient programs that will continue execution even under different failure conditions.

Workflows encapsulate the execution/orchestration of Tasks, including Activities and child Workflows.  They must also react to external events, deal with Timeouts, etc.

In this Clojure SDK programming model, a Temporal Workflow is a function declared with [defworkflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#defworkflow)

```clojure
(defworkflow my-workflow
   [params]
   ...)
```

## Implementing Workflows

A Workflow implementation consists of defining a (defworkflow) function.  The platform invokes this function each time a new Workflow execution is started or retried.  Returning from the method signals that the Workflow execution is considered complete.  The result is available to the caller via [temporal.client.core/get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#get-result).

### Example

```clojure
(require '[temporal.workflow :refer [defworkflow]])

(defworkflow my-workflow
    [{:keys [foo]}]
    ...)
```

### Workflow Implementation Constraints

Temporal uses the [Event Sourcing pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/event-sourcing) to recover the state of a Workflow object, including its threads and local variable values.  The Workflow code is re-executed from the beginning whenever a Workflow state requires restoration.  During replay, successfully executed Activities are not re-executed but return the result previously recorded in the Workflow event history.

Even though Temporal has the replay capability, which brings resilience to your Workflows, you should never think about this capability when writing your Workflows.  Instead, you should focus on implementing your business logic/requirements and write your Workflows as they would execute only once.

There are some things, however, to think about when writing your Workflows, namely determinism and isolation.  We summarize these constraints here:

- Do not use mutable global variables such as atoms in your Workflow implementations.  Avoiding globals will ensure that multiple Workflow instances are fully isolated.
- Do not call non-deterministic functions like non-seeded random or uuid generators directly from the Workflow code.  Instead, use Side Effects.
- Perform all IO operations and calls to third-party services on Activities and not Workflows, as they are usually non-deterministic.
- Do not use any programming language constructs that rely on system time.  All notions of time must come from Side Effects or Activities so that the results become part of the Event History.
- Do not use threading primitives such as clojure.core.async/go or clojure.core.async/thread.
- Do not perform any operations that may block the underlying thread, such as clojure.core.async/<!!.
  - There is no general need for explicit synchronization because multi-threaded code inside a Workflow is executed one thread at a time and under a global lock.
  - This Clojure SDK provides integration with [promesa](https://github.com/funcool/promesa) with a few limitations (See [Promises](#promises)) for asynchronous integration with safe blocking operations, such as waiting on an Activity.
- Use [versioning-support](#versioning) when making changes to the Workflow code.  Without this, any deployment of updated Workflow code might break already running Workflows.
- Don’t access configuration APIs directly from a Workflow because changes in the configuration might affect the Workflow's execution path.  Pass the configuration as an argument to a Workflow function or use an Activity to load it.

## Registering Workflows

By default, Workflows are automatically registered simply by declaring a (defworkflow).  You may optionally manually specify Workflows to register when creating Workers (see [temporal.client.worker/worker-options](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.worker#worker-options)).

*It should be noted that the name of the Workflow, the arguments and signals that the Workflow accepts, and the data that the workflow returns are all part of a contract you need to maintain across potentially long-lived instances.  Therefore, you should take care when refactoring code involving Workflow logic to avoid inadvertently breaking your contract.*

## Starting Workflow Executions

In this Clojure SDK, developers manage Workflows with the following flow:

1.  Invoke [temporal.client.core/create-workflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#create-workflow)
2.  Invoke [temporal.client.core/start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#start) or [temporal.client.core/signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#signal-with-start).  The `params` passed to these functions will be forwarded to the Workflow and available as `args` in the request map of the Workflow.
3.  Gather the asynchronous results with [temporal.client.core/get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#get-result), which returns a promise and requires dereferencing to realize the result.

### Example

```clojure
(defworkflow my-workflow
  [{:keys [foo]}]
  ...)

(let [w (create-workflow client my-workflow {:task-queue "MyTaskQueue"})]
    (start w {:foo "bar"})
    @(get-result w))
```

### Workflow ID Conflict Policy

When starting a workflow, you may encounter a situation where a workflow with the same ID is already running.  The `:workflow-id-conflict-policy` option controls this behavior:

| Policy | Description |
|--------|-------------|
| `:fail` | Fail with an error if workflow is already running (default) |
| `:use-existing` | Return a handle to the existing running workflow instead of starting a new one |
| `:terminate-existing` | Terminate the running workflow and start a new one |

This is particularly useful with [signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#signal-with-start) and [update-with-start](#update-with-start), where you want to interact with an existing workflow if running, or start a new one if not:

```clojure
(let [w (create-workflow client my-workflow {:task-queue "MyTaskQueue"
                                              :workflow-id "my-singleton-workflow"
                                              :workflow-id-conflict-policy :use-existing})]
    (signal-with-start w :my-signal {:data "value"} {:initial "args"})
    @(get-result w))
```

Note: This differs from `:workflow-id-reuse-policy`, which controls whether you can reuse an ID from a *completed/failed/terminated* workflow.  The conflict policy specifically handles *currently running* workflows.

## Safe blocking within Workflows

A Temporal Workflow instance behaves like a [Lightweight Process](https://en.wikipedia.org/wiki/Light-weight_process) or [Fiber](https://en.wikipedia.org/wiki/Fiber_(computer_science)).  These designs support a high ratio of Workflow instances to CPUs, often in the range of 1000:1 or greater.  Achieving this feat requires controlling the IO in and out of the instance to maximize resource sharing.  Therefore, any LWP/Fiber implementation will generally provide its own IO constructs (e.g., mailboxes, channels, promises, etc.), and Temporal is no exception.

In this Clojure SDK, this support comes in a few different flavors:

### Promises

Specific methods naturally return Workflow-safe Promises, such as invoking an Activity from a Workflow.  The Clojure SDK integrates these Workflow-safe Promises with the [promesa](https://github.com/funcool/promesa) library.  This section serves to document their use and limitations.

#### Safe to use

- Chaining primitives such as [then](https://funcool.github.io/promesa/latest/promesa.core.html#var-then.27), [map](https://funcool.github.io/promesa/latest/promesa.core.html#var-map), and [catch](https://funcool.github.io/promesa/latest/promesa.core.html#var-catch)

#### Unsafe to use

- "Originating" primitives, such as [create](https://funcool.github.io/promesa/latest/promesa.core.html#var-create), [resolved](https://funcool.github.io/promesa/latest/promesa.core.html#var-resolved), and [let](https://funcool.github.io/promesa/latest/promesa.core.html#var-let)
- Aggregating primitives, such as [all](https://funcool.github.io/promesa/latest/promesa.core.html#var-all) and [race](https://funcool.github.io/promesa/latest/promesa.core.html#var-race)

Instead, you must ensure that all promises originate with an SDK-provided function, such as [temporal.activity/invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#invoke) or [temporal.promise/rejected](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.promise#rejected).  For aggregating operations, see Temporal Safe options for [temporal.promise/all](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.promise#all) and [temporal.promise/race](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.promise#race).

What this means in practice is that any promise chain should generally start with some Temporal-native promise.

##### Bad Example

Do NOT do this:

```clojure
(require `[promesa.core :as p])

(-> (p/resolved true)
    (p/then (fn [x] (comment "do something with x"))))
```

Placing (p/resolved) (or anything else that ultimately creates a promesa promise) will not work, and you will receive a run-time error.

##### Good example

The proper method is to ensure that a Temporal native operation starts the chain.

```clojure
...
(require `[temporal.activity :as a])

(-> (a/invoke some-activity {:some "args"})
    (p/then (fn [x] (comment "do something with x"))))
```

##### Watch out for implicit conversion

The following situation can lead to a failure:

```clojure
(-> (when some-condition
      (a/invoke some-activity {:some "args"}))
    (p/then (fn [x] ...)))
```

For situations where `some-condition` is `false` because promesa will cast the scalar `nil` to (p/resolved nil), thus violating the origination rule.  Instead, do this:

```clojure
...
(require `[temporal.promise :as tp])

(-> (if some-condition
      (a/invoke some-activity {:some "args"})
      (tp/resolved false))
    (p/then (fn [x] ...)))
```

Doing so ensures that you meet the origination rules regardless of the conditional outcome.

### Await

You may use [temporal.workflow/await](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#await) to efficiently park the Workflow until a provided predicate evaluates to true.  The Temporal platform will re-evaluate the predicate at each major state transition of the Workflow.

### Temporal Signals

Your Workflow may send or receive [signals](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.signals).

#### Receiving Signals

##### Channel Abstraction using 'signal-chan'

This SDK provides a [core.async](https://github.com/clojure/core.async) inspired abstraction on [Temporal Signals](https://docs.temporal.io/workflows#signal) called signal-channels. To use signal channels, your Workflow may either block waiting with signals with [temporal.signals/<!](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.signals#%3C!) or use the non-blocking [temporal.signals/poll](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.signals#poll).  Either way, your Workflow needs first to obtain the `signal-chan` context obtained by  [temporal.signals/create-signal-chan](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.signals#create-signal-chan).

###### Example

```clojure
(require `[temporal.signals :as s])

(defworkflow my-workflow
  [args]
  (let [signals (s/create-signal-chan)
        message (<! signals "MySignal")]
    ...))
```

##### Raw Signal Handling

Alternatively, you may opt to handle signals directly with [temporal.signals/register-signal-handler!](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.signals#register-signal-handler!)

###### Example

```clojure
(defworkflow my-workflow
  [args]
  (let [state (atom 0)]
    (s/register-signal-handler! (fn [signal-name args]
                                  (swap! state inc)))
    (w/await (fn [] (> @state 1)))
    @state))
```

### Temporal Queries

Your Workflow may respond to [queries](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#query).

A temporal query is similar to a temporal signal; both are messages sent to a running Workflow.
The difference is that a signal intends to change the behaviour of the Workflow, whereas a query intends to inspect its current state.
Querying the state of a Workflow implies that the Workflow must maintain its state while running, typically in a Clojure [atom](https://clojuredocs.org/clojure.core/atom) or [ref](https://clojure.org/reference/refs).

#### Registering a Query handler

To enable querying a Workflow, you may use [temporal.workflow/register-query-handler!](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#register-query-handler!).
The query handler is a function that has a reference to the Workflow state, usually by closing over it.  It interprets the query and returns a response.

```clojure
(defworkflow stateful-workflow
  [{:keys [init] :as args}]
  (let [state (atom init)]
    (register-query-handler! (fn [query-type args]
                               (when (= query-type :my-query)
                                 (get-in @state [:path :to :answer]))))
    ;; e.g. react to signals (perhaps in a loop), updating the state atom
    ))
```

#### Querying a Workflow

You may query a Workflow with [temporal.client.core/query](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#query).
A query consists of a `query-type` (keyword) and possibly some `args` (any serializable data structure).

```clojure
(query workflow :my-query {:foo "bar"})
```

### Temporal Updates

Updates are similar to signals and queries but combine features of both: like signals, they can mutate workflow state; like queries, they return values to the caller.  Additionally, updates can perform blocking operations such as invoking activities or child workflows.

#### Registering an Update handler

To enable updates on a Workflow, use [temporal.workflow/register-update-handler!](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#register-update-handler!).
The update handler is a function that has a reference to the Workflow state, typically by closing over it.  It processes the update, optionally mutates state, and returns a response.

```clojure
(defworkflow stateful-workflow
  [{:keys [init] :as args}]
  (let [state (atom init)]
    (register-update-handler!
      (fn [update-type args]
        (when (= update-type :increment)
          (swap! state update :counter + (:amount args))
          @state)))
    ;; workflow implementation
    ))
```

You may optionally provide a validator function that runs before the update handler.  Validators must not mutate state or perform blocking operations:

```clojure
(register-update-handler!
  (fn [update-type args]
    (swap! state update :counter + (:amount args))
    @state)
  {:validator (fn [update-type args]
                (when (neg? (:amount args))
                  (throw (IllegalArgumentException. "amount must be positive"))))})
```

#### Sending Updates to a Workflow

You may send an update to a Workflow with [temporal.client.core/update](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#update).
An update consists of an `update-type` (keyword) and possibly some `args` (any serializable data structure).

```clojure
(update workflow :increment {:amount 5})
```

#### Asynchronous Updates

For cases where you want to send an update without blocking until completion, use [temporal.client.core/start-update](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#start-update).  This returns a handle that you can use to retrieve the result later:

```clojure
(let [handle (c/start-update workflow :increment {:amount 5})]
  ;; Do other work while update is processing...
  (println "Update ID:" (:id handle))
  ;; When ready, wait for the result
  (println "Result:" @(:result handle)))
```

The handle contains:
- `:id` - The unique identifier for this update
- `:execution` - The workflow execution info
- `:result` - A promise that resolves to the update result

You can customize the behavior with options:

```clojure
(c/start-update workflow :increment {:amount 5}
                {:update-id "my-idempotency-key"
                 :wait-for-stage :completed})  ;; :admitted, :accepted, or :completed
```

#### Retrieving Update Handles

To retrieve a handle for a previously initiated update, use [temporal.client.core/get-update-handle](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#get-update-handle):

```clojure
(let [handle (c/get-update-handle workflow "my-update-id")]
  @(:result handle))
```

#### Update-With-Start

To atomically start a workflow (if not running) and send an update, use [temporal.client.core/update-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#update-with-start).  This is useful for ensuring a workflow exists before sending an update:

```clojure
(let [workflow (c/create-workflow client my-workflow
                                  {:task-queue task-queue
                                   :workflow-id "my-workflow-id"
                                   :workflow-id-conflict-policy :use-existing})
      handle (c/update-with-start workflow :increment {:amount 5} {:initial-value 0})]
  @(:result handle))
```

**Important**: `update-with-start` requires the `:workflow-id-conflict-policy` option to be set in the workflow options (see [Workflow ID Conflict Policy](#workflow-id-conflict-policy)).

### Coordinating Concurrent Handlers

Temporal workflows execute on a single thread, so you don't need to worry about parallelism. However, you do need to consider concurrency when signal or update handlers can **block** (call activities, sleep, await, or child workflows).

**Non-blocking handlers** run to completion atomically. No lock needed:

```clojure
;; Non-blocking handler - runs atomically, no lock needed
(fn [update-type {:keys [amount]}]
  (swap! state update :counter + amount)
  @state)
```

**Blocking handlers** can be interrupted at await points, allowing other handlers to interleave. Use [temporal.workflow/new-lock](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#new-lock) and [temporal.workflow/with-lock](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#with-lock) when a handler performs blocking operations AND accesses shared state:

```clojure
;; PROBLEM: Handler could be interrupted at the activity call
(fn [update-type args]
  (let [current (:counter @state)]                       ;; read state
    (let [result @(a/invoke validate-activity current)]  ;; BLOCKS - other handlers can run!
      (swap! state assoc :validated result))))           ;; another handler may have changed state
```

Use a lock to prevent other handlers from interleaving:

```clojure
(defworkflow my-workflow
  [args]
  (let [state (atom {})
        lock (w/new-lock)]
    (w/register-update-handler!
      (fn [update-type args]
        (w/with-lock lock
          ;; Lock ensures no other handler runs during this sequence
          (let [current (:counter @state)]
            (let [result @(a/invoke validate-activity current)]
              (swap! state assoc :validated result)
              @state)))))
    ;; workflow implementation
    ))
```

### Ensuring Handlers Complete Before Workflow Ends

When handlers perform blocking operations (activities, child workflows, sleep, await), your workflow could complete before handlers finish their work. This can cause interrupted handler execution, client errors, or lost work.

Use [temporal.workflow/every-handler-finished?](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#every-handler-finished?) with [temporal.workflow/await](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#await) to wait for all handlers to complete:

```clojure
(defworkflow my-workflow
  [args]
  (let [state (atom {:counter 0})]
    (w/register-update-handler!
      (fn [update-type args]
        ;; Handler that does async work
        (let [result @(a/invoke process-activity args)]
          (swap! state assoc :processed result)
          @state)))
    ;; Do main workflow logic...
    (do-work state)
    ;; Wait for any in-progress handlers before completing
    (w/await w/every-handler-finished?)
    @state))
```

By default, your worker will log a warning when a workflow completes with unfinished handlers.

## Exceptions

This SDK integrates with the [slingshot](https://github.com/scgilardi/slingshot) library.  Stones cast with Slingshot's throw+ are serialized and re-thrown across activity and workflow boundaries in a transparent manner that is compatible with Slingshot's `try+` based catch blocks.

### Managing Retries
By default, stones cast that are not caught locally by an Activity or Workflow trigger ApplicationFailure semantics and are thus subject to the overall Retry Policies in place.   However, the developer may force a given stone to be non-retriable by setting the flag '::non-retriable?' within the object.

Example:

```clojure
(require `[temporal.exceptions :as e])
(require `[slingshot.slingshot :refer [throw+]])

(defactivity my-activity
   [ctx args]
   (throw+ {:type ::my-fatal-error :msg "this error is non-retriable" ::e/non-retriable? true}))
```

## Versioning

The Temporal Platform requires that Workflow code be deterministic.  Because of that requirement, this Clojure SDK exposes a workflow patching API [temporal.workflow/get-version](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#get-version).  Workflow developers use the `get-version` function to determine the correct branch of logic to follow to maintain determinism.

Example:

Assume we have a workflow that invokes an activity using temporal.activity/invoke that we wish to convert to temporal.activity/local-invoke.  Such a change is acceptable for any future instances of your Workflow.  However, any existing instances must be careful as this logic change could introduce non-determinism during replay.

We can safely handle both the original and the new desired scenario by branching based on the results from calling temporal.workflow/get-version:

```clojure
(require `[temporal.workflow :as w])
(require `[temporal.activity :as a])

(let [version (w/get-version ::local-activity w/default-version 1)]
    (cond
      (= version w/default-version)  @(a/invoke versioned-activity :v1)
      (= version 1)                  @(a/local-invoke versioned-activity :v2)))
```

## Continue-As-New

Long-running Workflows can accumulate large event histories.  To prevent unbounded growth, you can use [temporal.workflow/continue-as-new](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#continue-as-new) to complete the current Workflow execution and immediately start a new execution with the same Workflow ID but a fresh event history.

This is particularly useful for Workflows that:
- Process items in batches
- Run indefinitely (e.g., polling or subscription patterns)
- Accumulate state over time that can be summarized

```clojure
(require '[temporal.workflow :as w])

(defworkflow batch-processor
  [{:keys [batch-number processed-count]}]
  (let [items (fetch-next-batch)
        new-count (+ processed-count (count items))]
    (process-items items)
    (if (more-batches?)
      (w/continue-as-new {:batch-number (inc batch-number)
                          :processed-count new-count})
      {:total-processed new-count})))
```

Continue-as-new accepts an optional options map to override settings for the new run:

```clojure
(w/continue-as-new params {:task-queue "different-queue"
                           :workflow-run-timeout (Duration/ofHours 1)})
```

## Search Attributes

Search attributes are indexed metadata that can be used to filter and search for Workflows in the Temporal UI and via the Visibility API.

### Setting Search Attributes at Start

You can set search attributes when starting a Workflow:

```clojure
(c/create-workflow client my-workflow {:task-queue task-queue
                                       :search-attributes {"CustomerId" "12345"}})
```

### Upserting Search Attributes

To update search attributes during Workflow execution, use [temporal.workflow/upsert-search-attributes](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#upsert-search-attributes):

```clojure
(require '[temporal.workflow :as w])

(defworkflow order-workflow
  [{:keys [order-id]}]
  (w/upsert-search-attributes {"OrderStatus" {:type :keyword :value "processing"}})
  ;; ... process order ...
  (w/upsert-search-attributes {"OrderStatus" {:type :keyword :value "completed"}}))
```

Supported search attribute types:
- `:text` - Full-text searchable string
- `:keyword` - Exact-match string
- `:int` - 64-bit integer
- `:double` - 64-bit floating point
- `:bool` - Boolean
- `:datetime` - OffsetDateTime
- `:keyword-list` - List of exact-match strings

Note: Custom search attributes must be pre-registered with the Temporal server before use.
