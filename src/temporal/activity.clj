;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.activity
  "Methods for defining and invoking activity tasks"
  (:require [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [promesa.core :as p]
            [temporal.internal.activity :as a]
            [temporal.internal.utils :as u]
            [temporal.internal.promise])                    ;; needed for IPromise protocol extention
  (:import [io.temporal.workflow Workflow]))

(defn- complete-invoke
  [activity result]
  (log/trace activity "completed with" (count result) "bytes")
  (let [r (nippy/thaw result)]
    (log/trace activity "results:" r)
    r))

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
     (-> (.executeAsync stub act-name u/bytes-type (u/->objarray params))
         (p/then (partial complete-invoke activity))
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
  ([activity params] (invoke activity params {}))
  ([activity params options]
   (let [act-name (a/get-annotation activity)
         stub (Workflow/newUntypedLocalActivityStub (a/local-invoke-options-> options))]
     (log/trace "local-invoke:" activity "with" params options)
     (-> (.executeAsync stub act-name u/bytes-type (u/->objarray params))
         (p/then (partial complete-invoke activity))
         (p/catch (fn [e]
                    (log/error e)
                    (throw e)))))))

(defmacro defactivity
  "
Defines a new activity, similar to defn, expecting a 2-arity parameter list and body.  Should evaluate to something
serializable, which will be available to the [[invoke]] caller, or to a core.async channel (See Async Mode below).

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
    `(def ~name ^{::a/def {:name ~sname :fqn ~fqn}}
       (fn [ctx# args#]
         (log/trace (str ~fqn ": ") args#)
         (let [f# (fn ~params* (do ~@body))]
           (f# ctx# args#))))))
