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
