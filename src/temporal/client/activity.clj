;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.client.activity
  "Methods for dispatching Standalone Activities via `ActivityClient` (Temporal Java SDK 1.36+).

  Standalone Activities run independently of any workflow.  This namespace is the *external
  client* API — use it from application code, tests, or scripts outside a workflow context.

  For activities invoked *inside* a workflow, see [[temporal.activity/invoke]] instead.

  | `temporal.activity`        | `temporal.client.activity`                                 |
  | -------------------------- | ---------------------------------------------------------- |
  | Used inside a defworkflow  | Used outside any workflow (client / test / script code)    |
  | Dispatches via Workflow stub | Dispatches via ActivityClient                            |
  | Returns workflow-aware promise | Returns standard Promesa promise                       |
  "
  (:require [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.options :as copts]
            [temporal.internal.activity-client :as ac]
            [temporal.internal.exceptions :as e]
            [temporal.internal.utils :as u])
  (:import [io.temporal.client ActivityClient UntypedActivityHandle]
           [java.time Duration]))

(defn create-client
  "Creates an `ActivityClient` for dispatching Standalone Activities (Temporal Java SDK 1.36+).

  The client will not verify the connection before returning when called with one argument.
  Pass a `timeout` Duration (default 5s) to block until the connection is confirmed.

  Arguments:

  - `options`: Options for configuring the `ActivityClient`
    (See [[temporal.client.options/activity-client-options]] and [[temporal.client.options/stub-options]])
  - `timeout`: Optional connection timeout as a [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) (default: 5s)

  ```clojure
  (create-client {:target \"localhost:7233\" :namespace \"my-namespace\"})
  ```
  "
  ([] (create-client {}))
  ([options] (create-client options (Duration/ofSeconds 5)))
  ([options timeout]
   (let [service (copts/service-stub-> options timeout)]
     (ActivityClient/newInstance service (copts/activity-client-options-> options)))))

(defn start
  "Starts a Standalone Activity asynchronously, returning a handle map immediately.

  Arguments:

  - `client`: An `ActivityClient` created with [[create-client]]
  - `activity`: A [[temporal.activity/defactivity]] var, keyword, or string naming the activity type
  - `params`: Opaque serializable arguments passed to the activity
  - `options`: Activity execution options (see below)

  #### options map

  | Value                      | Description                                                                   | Type                              | Default   |
  | -------------------------- | ----------------------------------------------------------------------------- | --------------------------------- | --------- |
  | :task-queue                | Task queue the activity worker listens on (required)                          | String or keyword                 |           |
  | :id                        | Activity ID (auto-generated if omitted)                                       | String                            |           |
  | :id-reuse-policy           | Policy when reusing an activity ID                                            | See `id reuse policies` below     |           |
  | :id-conflict-policy        | Policy when a conflicting activity ID is already running                      | See `id conflict policies` below  |           |
  | :schedule-to-close-timeout | Maximum time from schedule to completion                                      | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
  | :schedule-to-start-timeout | Maximum time the activity can wait in the task queue                          | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
  | :start-to-close-timeout    | Maximum time for a single execution attempt                                   | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 3 seconds |
  | :heartbeat-timeout         | Maximum time between heartbeats                                               | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
  | :retry-options             | Retry configuration                                                           | [[temporal.common/retry-options]] |           |
  | :start-delay               | Delay before the activity is scheduled                                        | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
  | :static-summary            | Short summary visible in the Temporal UI                                      | String                            |           |
  | :static-details            | Longer details visible in the Temporal UI                                     | String                            |           |

  #### id reuse policies

  | Value                        | Description                                                           |
  | ---------------------------- | --------------------------------------------------------------------- |
  | :allow-duplicate             | Allow a new activity with the same ID after the previous one completes |
  | :allow-duplicate-failed-only | Allow reuse only if the previous activity failed                      |
  | :reject-duplicate            | Reject any attempt to start an activity with a duplicate ID           |

  #### id conflict policies

  | Value          | Description                                                   |
  | -------------- | ------------------------------------------------------------- |
  | :fail          | Fail if an activity with the same ID is already running       |
  | :use-existing  | Return a handle to the already-running activity               |

  Returns a map with:

  | Key               | Description                                                             |
  | ----------------- | ----------------------------------------------------------------------- |
  | :activity-id      | The activity ID assigned by the server                                  |
  | :activity-run-id  | The run ID for this activity execution                                  |
  | :result           | A Promesa promise that resolves to the activity's return value          |
  | :handle           | The raw `UntypedActivityHandle` (used by [[cancel]], [[terminate]], [[describe]]) |

  ```clojure
  (defactivity greet-activity
    [_ {:keys [name]}]
    (str \"Hello, \" name))

  (let [handle (start client greet-activity {:name \"World\"}
                      {:task-queue :my-queue
                       :start-to-close-timeout (Duration/ofSeconds 10)})]
    @(:result handle))
  ;; => \"Hello, World\"
  ```
  "
  ([client activity params] (start client activity params {}))
  ([client activity params options]
   (let [act-name (ac/resolve-activity-name activity)
         handle   (ac/start-untyped-handle client act-name options params)]
     (log/trace "start:" act-name "options:" options)
     {:activity-id     (.getActivityId ^UntypedActivityHandle handle)
      :activity-run-id (.getActivityRunId ^UntypedActivityHandle handle)
      :result          (-> (.getResultAsync ^UntypedActivityHandle handle u/object-type)
                           (p/catch e/slingshot? e/recast-stone)
                           (p/catch (fn [e]
                                      (log/error e)
                                      (throw e))))
      :handle          handle})))

(defn execute
  "Executes a Standalone Activity synchronously, blocking until it completes.

  Equivalent to `@(:result (start client activity params options))`.

  Arguments:

  - `client`: An `ActivityClient` created with [[create-client]]
  - `activity`: A [[temporal.activity/defactivity]] var, keyword, or string naming the activity type
  - `params`: Opaque serializable arguments passed to the activity
  - `options`: Activity execution options (see [[start]] for the full option map)

  ```clojure
  (execute client greet-activity {:name \"World\"}
           {:task-queue :my-queue
            :start-to-close-timeout (Duration/ofSeconds 10)})
  ;; => \"Hello, World\"
  ```
  "
  ([client activity params] (execute client activity params {}))
  ([client activity params options]
   (let [act-name (ac/resolve-activity-name activity)
         handle   (ac/start-untyped-handle client act-name options params)]
     (log/trace "execute:" act-name "options:" options)
     (.getResult ^UntypedActivityHandle handle u/object-type))))

(defn cancel
  "Requests cancellation of a Standalone Activity identified by `handle`.

  The handle map is returned by [[start]].  The activity implementation receives
  cancellation via [[temporal.activity/get-cancellation-token]] or the next heartbeat check.

  Arguments:

  - `handle`: Handle map returned by [[start]]
  - `reason`: Optional human-readable cancellation reason string

  ```clojure
  (let [h (start client my-activity params opts)]
    (cancel h \"timed out waiting\"))
  ```
  "
  ([{:keys [^UntypedActivityHandle handle]}]
   (.cancel handle))
  ([{:keys [^UntypedActivityHandle handle]} reason]
   (.cancel handle ^String reason)))

(defn terminate
  "Forcefully terminates a Standalone Activity identified by `handle`.

  Unlike [[cancel]], termination is immediate and does not allow the activity
  to clean up.

  Arguments:

  - `handle`: Handle map returned by [[start]]
  - `reason`: Optional human-readable termination reason string

  ```clojure
  (terminate h \"unresponsive\")
  ```
  "
  ([{:keys [^UntypedActivityHandle handle]}]
   (.terminate handle))
  ([{:keys [^UntypedActivityHandle handle]} reason]
   (.terminate handle ^String reason)))

(defn describe
  "Returns an `ActivityExecutionDescription` for the activity identified by `handle`.

  The description includes metadata about the running activity execution such as
  its current status, schedule time, and last heartbeat.

  Arguments:

  - `handle`: Handle map returned by [[start]]

  ```clojure
  (describe h)
  ```
  "
  [{:keys [^UntypedActivityHandle handle]}]
  (.describe handle))
