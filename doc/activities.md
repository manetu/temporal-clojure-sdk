# Activities

## What is an Activity?

Activities are implementations of certain tasks which need to be performed during a Workflow execution. They can be used to interact with external systems, such as databases, services, etc.

Workflows orchestrate invocations of Activities.

## Implementing Activities

An Activity implementation consists of defining a (defactivity) function. This function is invoked by the platform each time an Activity execution is started or retried. As soon as this method returns, the Activity execution is considered as completed and the result is available to the caller.

### Example

```clojure
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity greet-activity
    [ctx {:keys [name] :as args}]
    (str "Hi, " name))
```

## Registering Activities

By default, Activities are automatically registered simply by declaring a (defactivity).  You may optionally manually declare specific Activities to register when creating Workers (see [worker-options](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.worker#worker-options)).

*It should be noted that the name of the Activity, the arguments that the Activity accepts, and the data that the Activity returns are all part of a contract that you need to maintain across potentially long-lived instances.  Therefore, the Activity definition must be treated with care whenever code is refactored.*

## Starting Activity Executions

In this Clojure SDK, Activities are always started with either [invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#invoke) or [local-invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.activity#local-invoke), both of which are called similarly.  The primary difference between them is the execution model under the covers (See [What is a Local Activity](https://docs.temporal.io/concepts/what-is-a-local-activity/))

### Example

```clojure
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity my-activity
   [ctx {:keys [foo] :as args}]
   ...)

(a/invoke my-activity {:foo "bar"})
```

## Asynchronous Mode

Returning a core.async channel places the activity into [Asynchronous mode](https://docs.temporal.io/java/activities/#asynchronous-activity-completion), where the result may be resolved at a future time by sending a single message on the channel. Sending a [Throwable](https://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html) will signal a failure of the activity. Any other value will be serialized and returned to the caller.

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