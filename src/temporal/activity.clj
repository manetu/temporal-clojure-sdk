;; Copyright © 2022-2023 Manetu, Inc.  All rights reserved

(ns temporal.activity
  "Methods for defining and invoking activity tasks"
  (:require [promesa.core :as p]
            [promesa.protocols :as pt]
            [taoensso.timbre :as log]
            [temporal.internal.activity :as a]
            [temporal.internal.exceptions :as e]
            [temporal.internal.promise :as ip]
            [temporal.internal.utils :as u])
  (:import [io.temporal.workflow Workflow]
           [io.temporal.activity Activity]))

(defn heartbeat
  "
Used to notify the Workflow Execution that the Activity Execution is alive.

Arguments:

- `details`: The details are accessible through [[get-heartbeat-details]] on the next Activity Execution retry.
"
  [details]
  (let [ctx (Activity/getExecutionContext)]
    (log/trace "heartbeat:" details)
    (.heartbeat ctx details)))

(defn get-heartbeat-details
  "
Extracts Heartbeat details from the last failed attempt. This is used in combination with retry options. An Activity
Execution could be scheduled with optional [[temporal.common/retry-options]]. If an Activity Execution failed then the
server would attempt to dispatch another Activity Task to retry the execution according to the retry options. If there
were Heartbeat details reported by [[heartbeat]] in the last Activity Execution that failed, they would be delivered
along with the Activity Task for the next retry attempt and can be extracted by the Activity implementation.
"
  []

  (let [ctx (Activity/getExecutionContext)
        details (.getHeartbeatDetails ctx u/object-type)
        v (when (.isPresent details)
            (.get details))]
    (log/trace "get-heartbeat-details:" v)
    v))

(defn get-info
  "Returns information about the Activity execution as a map.

Keys always present:

| Key             | Description                                             |
| --------------- | ------------------------------------------------------- |
| :task-token     | Opaque task token identifying this execution attempt    |
| :activity-id    | Unique ID for this activity instance                    |
| :activity-type  | The registered name of the activity type                |
| :in-workflow?   | `true` when running inside a workflow, `false` for standalone activities (Temporal Java SDK 1.35+) |

Keys present only when `:in-workflow?` is `true` (nil for Standalone Activities):

| Key          | Description                               |
| ------------ | ----------------------------------------- |
| :workflow-id | ID of the parent workflow execution       |
| :run-id      | Run ID of the parent workflow execution   |

Use [[in-workflow?]] as a guard before relying on `:workflow-id` or `:run-id`."
  []
  (a/get-info))

(defn in-workflow?
  "Returns `true` when this activity is executing as part of a workflow.

When `false` the activity is a Standalone Activity (introduced in Temporal Java SDK 1.35) and
[[get-info]] will return `nil` for `:workflow-id`, `:run-id`, and related workflow fields."
  []
  (:in-workflow? (a/get-info)))

(defn get-cancellation-token
  "
Returns an [ActivityCancellationToken](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/activity/ActivityCancellationToken.html)
that can be used to detect activity cancellation without requiring a heartbeat response. Unlike [[heartbeat]],
this allows cancellation detection even in activities that do not heartbeat.

**Requires a recent Temporal server version** to deliver cancellation via this path.
Introduced in Temporal Java SDK 1.36.

Use the native helper functions below to interact with the token without Java interop:
- [[cancellation-requested?]] — non-blocking boolean check
- [[throw-if-cancelled!]] — throw if cancelled (convenient for checkpointed loops)
- [[cancellation-future]] — promesa-compatible promise that completes on cancellation

```clojure
(defactivity my-activity
  [_ _]
  (let [token (a/get-cancellation-token)]
    (loop []
      (Thread/sleep 100)
      (if (a/cancellation-requested? token)
        :cancelled
        (recur)))))
```
"
  []
  (let [ctx (Activity/getExecutionContext)]
    (.getCancellationToken ctx)))

(defn cancellation-requested?
  "Returns `true` if cancellation has been requested for this activity execution, `false` otherwise.

Non-blocking. Use in polling loops together with [[get-cancellation-token]].

See also [[throw-if-cancelled!]] for a throw-on-cancel variant."
  [token]
  (.isCancellationRequested token))

(defn throw-if-cancelled!
  "Throws `ActivityCanceledException` if cancellation has been requested for this activity execution.
No-op otherwise. Useful as a lightweight cancellation checkpoint inside loops.

See also [[cancellation-requested?]] for a non-throwing variant."
  [token]
  (.throwIfCancellationRequested token))

(defn cancellation-future
  "Returns a promesa-compatible promise that completes (with `nil`) when cancellation is requested.

Useful for waiting on cancellation concurrently without polling. Deref (`@`) will block until
the activity is cancelled. Compose with promesa combinators (e.g. `p/race`) as needed.

See also [[cancellation-requested?]] for a non-blocking state check."
  [token]
  (->> (.getCancellationFuture token)
       ip/->temporal
       pt/-promise))

(defn invoke
  "
Invokes 'activity' with 'params' from within a workflow context.  Returns a promise that when derefed will resolve to
the evaluation of the defactivity once the activity concludes.

Arguments:

- `activity`: A reference to a symbol registered with [[defactivity]].
- `params`: Opaque serializable data that will be passed as arguments to the invoked activity
- `options`: See below.

#### options map

| Value                      | Description                                                                                | Type         | Default |
| -------------------------  | ------------------------------------------------------------------------------------------ | ------------ | ------- |
| :cancellation-type         | Defines the activity's stub cancellation mode.                                             | See `cancellation types` below | :try-cancel |
| :heartbeat-timeout         | Heartbeat interval.                                                                        | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :retry-options             | Define how activity is retried in case of failure.                                         | [[temporal.common/retry-options]] | |
| :start-to-close-timeout    | Maximum time of a single Activity execution attempt.                                       | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 3 seconds |
| :schedule-to-close-timeout | Total time that a workflow is willing to wait for Activity to complete.                    | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :schedule-to-start-timeout | Time that the Activity Task can stay in the Task Queue before it is picked up by a Worker. | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |

#### cancellation types

| Value                        | Description                                                                 |
| -------------------------    | --------------------------------------------------------------------------- |
| :try-cancel                  | In case of activity's scope cancellation send an Activity cancellation request to the server, and report cancellation to the Workflow Execution by causing the activity stub call to fail with [CancelledFailure](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/failure/CanceledFailure.html) |
| :abandon                     | Do not request cancellation of the Activity Execution at all (no request is sent to the server) and immediately report cancellation to the Workflow Execution by causing the activity stub call to fail with [CancelledFailure](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/failure/CanceledFailure.html) immediately. |
| :wait-cancellation-completed | Wait for the Activity Execution to confirm any requested cancellation.      |

```clojure
(defactivity my-activity
   [ctx {:keys [foo] :as args}]
   ...)

(invoke my-activity {:foo \"bar\"} {:start-to-close-timeout (Duration/ofSeconds 3))
```
"
  ([activity params] (invoke activity params {}))
  ([activity params options]
   (let [act-name (a/get-annotation activity)
         stub (Workflow/newUntypedActivityStub (a/invoke-options-> options))]
     (log/trace "invoke:" activity "with" params options)
     (-> (.executeAsync stub act-name u/object-type (u/->objarray params))
         (p/then (partial u/complete-invoke activity))
         (p/catch e/slingshot? e/recast-stone)
         (p/catch (fn [e]
                    (log/error e)
                    (throw e)))))))

(defn local-invoke
  "
Invokes 'activity' with 'params' from within a workflow context as a [Local Activity](https://docs.temporal.io/concepts/what-is-a-local-activity/).  Returns a promise that when
derefed will resolve to the evaluation of the defactivity once the activity concludes.

Arguments:

- `activity`: A reference to a symbol registered with [[defactivity]].
- `params`: Opaque serializable data that will be passed as arguments to the invoked activity
- `options`: See below.

#### options map

| Value                      | Description                                                                                | Type         | Default |
| -------------------------  | ------------------------------------------------------------------------------------------ | ------------ | ------- |
| :start-to-close-timeout    | Maximum time of a single Activity execution attempt.                                       | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :schedule-to-close-timeout | Total time that a workflow is willing to wait for Activity to complete.                    | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :retry-options             | Define how activity is retried in case of failure.                                         | [[temporal.common/retry-options]] | |
| :do-not-include-args       | When set to true, the serialized arguments of the local Activity are not included in the Marker Event that stores the local Activity's invocation result. | boolean | false |
| :local-retry-threshold     | Maximum time to retry locally, while keeping the Workflow Task open via a Heartbeat.       | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |

```clojure
(defactivity my-activity
   [ctx {:keys [foo] :as args}]
   ...)

(local-invoke my-activity {:foo \"bar\"} {:start-to-close-timeout (Duration/ofSeconds 3))
```
"
  ([activity params] (local-invoke activity params {}))
  ([activity params options]
   (let [act-name (a/get-annotation activity)
         stub (Workflow/newUntypedLocalActivityStub (a/local-invoke-options-> options))]
     (log/trace "local-invoke:" activity "with" params options)
     (-> (.executeAsync stub act-name u/object-type (u/->objarray params))
         (p/then (partial u/complete-invoke activity))
         (p/catch e/slingshot? e/recast-stone)
         (p/catch (fn [e]
                    (log/error e)
                    (throw e)))))))

(defmacro defactivity
  "
Defines a new activity, similar to defn. Expects a 2-arity parameter list and body that evaluates to a serializable
value (available to the [[invoke]] caller) or a core.async channel (see Async Mode below).

#### Async Mode:
Returning a core.async channel places the activity into
[Asynchronous](https://docs.temporal.io/java/activities/#asynchronous-activity-completion) mode, where the result may
be resolved at a future time by sending a single message on the channel.  Sending a
[Throwable](https://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html) will signal a failure of the activity.
Any other value will be serialized and returned to the caller.

Arguments:

- `ctx`: Context passed through from [[temporal.client.worker/start]]
- `args`: Passed from 'params' of [[invoke]]

```clojure
(defactivity greet-activity
    [ctx {:keys [name] :as args}]
    (str \"Hi, \" name))
```
"
  [name params* & body]
  (let [fqn (u/get-fq-classname name)
        sname (str name)]
    `(def ~name
       (with-meta
         (fn [ctx# args#]
           (log/trace (str ~fqn ": ") args#)
           (let [f# (fn ~params* (do ~@body))]
             (f# ctx# args#)))
         {::a/def {:name ~sname :fqn ~fqn}
          ::u/var (var ~name)}))))
