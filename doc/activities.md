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
