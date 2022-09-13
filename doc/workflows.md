# Workflows

## What is a Workflow?

Workflows are resilient programs, meaning that they will continue execution even in the presence of different failure conditions.

Workflows encapsulate execution/orchestration of Tasks which include Activities and child Workflows. They also need to react to external events, deal with Timeouts, etc.

In this Clojure SDK programming model, a Temporal Workflow is a function declared with ([defworkflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.workflow#defworkflow))

```clojure
(defworkflow my-workflow
   [ctx params]
   ...)
```

## Implementing Workflows

A Workflow implementation consists of defining a (defworkflow) function. This function is invoked by the platform each time a new Workflow execution is started or retried. As soon as this method returns, the Workflow execution is considered as completed and the result is available to the caller via ([get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.client.core#get-result)).

### Example

```clojure
(require '[temporal.workflow :refer [defworkflow]])

(defworkflow my-workflow
    [ctx {{:keys [foo]} :args}]
    ...)
```

### Workflow Implementation Constraints

Temporal uses the [Event Sourcing pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/event-sourcing) to recover the state of a Workflow object including its threads and local variable values. In essence, every time a Workflow state has to be restored, its code is re-executed from the beginning. Note that during replay, successfully executed Activities are not re-executed as their results are already recorded in the Workflow event history.

Even though Temporal has the replay capability, which brings resilience to your Workflows, you should never think about this capability when writing your Workflows. Instead, you should focus on implementing your business logic/requirements and write your Workflows as they would execute only once.

There are some things however to think about when writing your Workflows, namely determinism and isolation. We summarize these constraints here:

- Do not use any mutable global variables such as atoms in your Workflow implementations. This will assure that multiple Workflow instances are fully isolated.
- Do not call any non-deterministic functions like non-seeded random or uuid-generators directly from the Workflow code. (Coming soon: SideEffect API)
- Perform all IO operations and calls to third-party services on Activities and not Workflows, as they are usually non-deterministic in nature.
- Do not use any programming language constructs that rely on system time. (Coming soon: API methods for time)
- Do not use threading primitives such as clojure.core.async/go or clojure.core.async/thread. (Coming soon: API methods for async function execution)
- Do not perform any operations that may block the underlying thread, such as clojure.core.async/<!!.
  - There is no general need in explicit synchronization because multi-threaded code inside a Workflow is executed one thread at a time and under a global lock.
  - This Clojure SDK provides integration with [promesa](https://github.com/funcool/promesa) with a few limitations (See [Promises](#promises)), for asynchronous integration with safe blocking operations, such as waiting on an Activity.
- (Coming soon) Use versioning-support when making any changes to the Workflow code. Without this, any deployment of updated Workflow code might break already running Workflows.
- Don’t access configuration APIs directly from a Workflow because changes in the configuration might affect a Workflow execution path. Pass it as an argument to a Workflow function or use an Activity to load it.

## Registering Workflows

By default, Workflows are automatically registered simply by declaring a (defworkflow).  You may optionally manually declare specific Workflows to register when creating Workers (see [worker-options](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.client.worker#worker-options)).

*It should be noted that the name of the workflow is part of a contract, along with the arguments that the workflow accepts.  Therefore, the Workflow definition must be treated with care whenever code is refactored.*

## Starting Workflow Executions

In this Clojure SDK, Workflows are always started with the following flow:

1. Invoke [create-workflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.client.core#create-workflow)
2. Invoke [start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.client.core#start) or [signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.client.core#signal-with-start).  The `params` passed to these functions will be forwarded to the workflow and available as `args` in the request map of the Workflow.
3. Gather the asynchronous results with [get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.client.core#get-result) which returns a promise and needs to be dereferenced.

### Example

```clojure
(defworkflow my-workflow
  [ctx {{:keys [foo]} :args}] 
  ...)

(let [w (create-workflow client my-workflow {:task-queue "MyTaskQueue"})]
    (start w {:foo "bar"})
    @(get-result w))
```

## Safe blocking within Workflows

The Temporal Workflow instance behaves like a [Lightweight Process](https://en.wikipedia.org/wiki/Light-weight_process) or [Fiber](https://en.wikipedia.org/wiki/Fiber_(computer_science)).  This means the system can generally support a high ratio of Workflow instances to CPUs often in the range of 1000:1 or greater.  Achieving this feat requires controlling the IO in and out of the instance in a way that maximizes resource sharing.  Therefore, any LWP/Fiber implementation will generally provide its own IO constructions (e.g. mailboxes, channels, promises, etc.), and Temporal is no exception.

In this Clojure SDK, this support comes in a few different flavors:

### Promises

Certain methods naturally return Workflow-safe Promises, such as invoking an Activity from a Workflow.  These Workflow-safe Promises have been integrated with the [promesa](https://github.com/funcool/promesa) library.  This section serves to document their use and limitations.

#### Safe to use

- Chaining primitives such as [then](https://funcool.github.io/promesa/latest/promesa.core.html#var-then.27), [map](https://funcool.github.io/promesa/latest/promesa.core.html#var-map), and [catch](https://funcool.github.io/promesa/latest/promesa.core.html#var-catch)

#### Unsafe to use

- "Originating" primitives, such as [create](https://funcool.github.io/promesa/latest/promesa.core.html#var-create), [resolved](https://funcool.github.io/promesa/latest/promesa.core.html#var-resolved), and [let](https://funcool.github.io/promesa/latest/promesa.core.html#var-let)
- Aggregating primitives, such as [all](https://funcool.github.io/promesa/latest/promesa.core.html#var-all) and [race](https://funcool.github.io/promesa/latest/promesa.core.html#var-race)

Instead, you must ensure that all promises originate with an SDK provided function, such as [invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.activity#invoke) or [rejected](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.promise#rejected).  For aggregating operations, see Temporal Safe options for [all](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.promise#all) and [race](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.promise#race).

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

The proper method is to ensure that a Temporal native operation starts the chain

```clojure
...
(require `[temporal.activity :as a])

(-> (a/invoke some-activity {:some "args"})
    (p/then (fn [x] (comment "do something with x"))))
```

##### Watch out for implicit conversion

The following situation can lead to a failure

```clojure
(-> (when some-condition
      (a/invoke some-activity {:some "args"}))
    (p/then (fn [x] ...)))
```

for situations where some-condition is `false` because promesa will cast the scalar `nil` to (p/resolved nil), thus violating the origination rule. Instead, do this:

```clojure
...
(require `[temporal.promise :as tp])

(-> (if some-condition
      (a/invoke some-activity {:some "args"})
      (tp/resolved false))
    (p/then (fn [x] ...)))
```

Thus ensuring that the origination rules are met regardless of the outcome of the conditional.

### Await

You may use [await](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.core#await) to efficiently parks the Workflow until a provided predicate evaluates to true.  The predicate is evaluated at each major state transition of the Workflow.

### Temporal Signals

Your Workflow may send or receive [signals](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.signals).

#### Receiving Signals

Your Workflow may either block waiting with signals with [<!](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.signals#%3C!) or use the non-blocking [poll](https://cljdoc.org/d/io.github.manetu/temporal-sdk/0.7.0/api/temporal.signals#poll).  In either case, your Workflow needs to obtain the `signals` context provided in the Worklow request map.

##### Example

```clojure
(defworkflow my-workflow
  [ctx {:keys [signals]}]
  (let [message (<! signals "MySignal")]
    ...))
```