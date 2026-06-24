# Activities

## What is an Activity?

Activities are implementations of specific tasks performed during a Workflow execution.  These tasks typically interact with external systems, such as databases, services, etc.  They are free to block using conventional synchronization primitives or to run complex operations.  An Activity forms the basis of a checkpoint, because the values entering and leaving an Activity become part of the Event History.

Workflows orchestrate invocations of Activities.

## Implementing Activities

An Activity implementation consists of defining a (defactivity) function.  The platform invokes this function each time an Activity execution is started by a Workflow or retried by the platform.  Returning from the method signals that the Activity is considered complete.  The result is then made available to the calling Workflow.

### Example

```clojure
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity greet-activity
    [ctx {:keys [name] :as args}]
    (str "Hi, " name))
```

## Registering Activities

By default, Activities are automatically registered simply by declaring a (defactivity).  You may optionally manually specify Activities to register when creating Workers (see [temporal.client.worker/worker-options](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.worker#worker-options)).

*It should be noted that the name of the Activity, the arguments that the Activity accepts, and the data that the Activity returns are all part of a contract you need to maintain across potentially long-lived instances.  Therefore, the Activity definition must be treated with care whenever refactoring code.*

## Starting Activity Executions

In this Clojure SDK, Workflows start Activities with either [temporal.activity/invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#invoke) or [temporal.activity/local-invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#local-invoke), both of which share similar calling conventions.  The primary difference between them is the execution model under the covers (See [What is a Local Activity](https://docs.temporal.io/concepts/what-is-a-local-activity/))

### Example

```clojure
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity my-activity
   [ctx {:keys [foo] :as args}]
   ...)

(a/invoke my-activity {:foo "bar"})
```

## Activity Context

Within an activity implementation, you can inspect runtime information about the current execution via [temporal.activity/get-info](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#get-info).

### Standard Context Fields

| Key              | Description                                                                          |
| ---------------- | ------------------------------------------------------------------------------------ |
| `:task-token`    | Opaque task token identifying this execution attempt                                 |
| `:activity-id`   | Unique ID for this activity instance                                                 |
| `:activity-type` | The registered name of the activity type                                             |
| `:in-workflow?`  | `true` when running inside a workflow, `false` for standalone activities (Temporal Java SDK 1.35+) |

```clojure
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity my-activity
  [_ _]
  (let [info (a/get-info)]
    (log/info "Activity ID:" (:activity-id info))
    ...))
```

### Workflow-Scoped Fields (nullable in Standalone Context)

The `:workflow-id` and `:run-id` fields are only populated when the activity runs as part of a workflow execution. When running as a **standalone activity** (invoked directly without a workflow, a feature introduced in Temporal Java SDK 1.35), these fields are `nil`.

Use [temporal.activity/in-workflow?](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#in-workflow?) before accessing these fields to avoid null-pointer errors:

```clojure
(defactivity my-activity
  [_ _]
  (when (a/in-workflow?)
    (let [{:keys [workflow-id run-id]} (a/get-info)]
      (log/info "Parent workflow:" workflow-id "run:" run-id))))
```

### `temporal.activity` vs `temporal.client.activity`

| Namespace                    | Usage                                                                        |
| ---------------------------- | ---------------------------------------------------------------------------- |
| `temporal.activity`          | Activities executed by workers as part of workflow orchestration (current)   |
| `temporal.client.activity`   | Direct standalone activity invocation outside a workflow context (Temporal Java SDK 1.35+) |

Activities defined with `defactivity` in `temporal.activity` work in both contexts. The difference is in how they are invoked: via `a/invoke` inside a workflow, or via `temporal.client.activity` for standalone execution. In standalone context, `:in-workflow?` returns `false` and workflow-scoped fields are `nil`.

## Cancellation

### Detecting Cancellation via Heartbeat

The traditional way to detect activity cancellation is to call [`heartbeat`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#heartbeat) and catch `ActivityCanceledException`.

### Detecting Cancellation via Cancellation Token

Temporal Java SDK 1.36 introduced [`get-cancellation-token`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#get-cancellation-token), which provides cancellation detection **without** requiring a heartbeat. This is useful for activities that do not otherwise heartbeat.

> **Note:** Cancellation delivery via this API requires a recent Temporal server version.

Use the native helper functions to interact with the token without Java interop:

#### Polling (non-blocking check)

Use [`cancellation-requested?`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#cancellation-requested?) to check cancellation state without blocking:

```clojure
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity my-activity
  [_ _]
  (let [token (a/get-cancellation-token)]
    (loop []
      (Thread/sleep 100)
      (if (a/cancellation-requested? token)
        :cancelled
        (recur)))))
```

#### Throwing on cancellation

Use [`throw-if-cancelled!`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#throw-if-cancelled!) as a lightweight checkpoint; it throws `ActivityCanceledException` if cancelled, no-ops otherwise:

```clojure
(defactivity my-activity
  [_ _]
  (let [token (a/get-cancellation-token)]
    (dotimes [_ 10]
      (a/throw-if-cancelled! token)
      (Thread/sleep 500))
    :completed))
```

#### Awaiting cancellation as a promise

Use [`cancellation-future`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#cancellation-future) to obtain a promesa-compatible promise that completes when cancellation is requested:

```clojure
(defactivity my-activity
  [_ _]
  (let [token (a/get-cancellation-token)]
    @(a/cancellation-future token)   ;; blocks until cancelled
    :cancelled))
```

## Asynchronous Mode

Returning a core.async channel places the Activity into [Asynchronous mode](https://docs.temporal.io/java/activities/#asynchronous-activity-completion).  The Activity is then free to send a single message on the channel at a future time to signal completion.  The value sent is returned to the calling Workflow.  Sending a [Throwable](https://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html) will signal a failure of the Activity.

### Example

```clojure
(require '[temporal.activity :refer [defactivity] :as a])
(require '[clojure.core.async :refer [go <!] :as async])

(defactivity async-greet-activity
    [ctx {:keys [name] :as args}] 
    (go
      (<! (async/timeout 1000))
      (str "Hi, " name)))
```
