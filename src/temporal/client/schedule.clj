(ns temporal.client.schedule
  (:require [taoensso.timbre :as log]
            [temporal.client.options :as copts]
            [temporal.internal.utils :as u]
            [temporal.internal.schedule :as s])
  (:import [java.time Duration]
           [io.temporal.client.schedules ScheduleClient ScheduleUpdate ScheduleUpdateInput]))

(defn create-client
  "Creates a `ScheduleClient` instance suitable for interacting with Temporal's Schedules.

   Arguments:

   - `options`: Options for configuring the `ScheduleClient` (See [[temporal.client.options/schedule-client-options]] and [[temporal.client.options/stub-options]])
   - `timeout`: Connection timeout as a [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) (default: 5s)

"
  ([options] (create-client options (Duration/ofSeconds 5)))
  ([options timeout]
   (let [service (copts/service-stub-> options timeout)]
     (ScheduleClient/newInstance service (copts/schedule-client-options-> options)))))

(defn schedule
  "Creates a `Schedule` with Temporal

   Arguments:

   - `client`: [ScheduleClient](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/schedules/ScheduleClient.html)
   - `schedule-id`: The string name of the schedule in Temporal, keeping it consistent with workflow id is a good idea
   - `options`: A map containing the `:schedule`, `:state`, `:policy`, `:spec`, and `:action` option maps for the `Schedule`

   `:schedule` options:

   | Value                   | Description                                                 | Type    |
   |-------------------------|-------------------------------------------------------------|---------|
   | :trigger-immediately?   | Trigger one action immediately when the schedule is created | boolean |
   | :memo                   | Arbitrary non-indexed metadata map                          | Map     |
   | :search-attributes      | Indexed schedule-level attributes. Supports simple and typed formats; see [Search attribute input formats](/doc/workflows.md#search-attribute-input-formats). | Map |

   `:state` options:

   | Value               | Description                                                | Type    |
   |---------------------|------------------------------------------------------------|---------|
   | :paused?            | Whether the schedule starts paused                         | boolean |
   | :note               | Human-readable note describing the current state           | String  |
   | :limited-action?    | Limit the schedule to `:remaining-actions` executions      | boolean |
   | :remaining-actions  | Number of actions remaining when `:limited-action?` is set | long |

   `:policy` options:

   | Value               | Description                                          | Type |
   |---------------------|------------------------------------------------------|------|
   | :overlap            | Overlap policy: `:allow`, `:buffer`, `:buffer-one`, `:cancel`, `:skip`, `:terminate` | keyword |
   | :catchup-window     | Maximum catch-up window for missed actions           | [Duration](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html) |
   | :pause-on-failure?  | Pause the schedule if a workflow action fails        | boolean |

   `:spec` options:

   | Value              | Description                                           | Type |
   |--------------------|-------------------------------------------------------|------|
   | :cron-expressions  | Cron expressions in [Temporal cron syntax](https://docs.temporal.io/cron-job)   | List of String |
   | :calendars         | Calendar specs                                        | List of [ScheduleCalendarSpec](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/schedules/ScheduleCalendarSpec.html) |
   | :intervals         | Interval specs                                        | List of [ScheduleIntervalSpec](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/schedules/ScheduleIntervalSpec.html) |
   | :start-at          | Time before which no actions are taken                | [Instant](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Instant.html) |
   | :end-at            | Time after which no actions are taken                 | [Instant](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Instant.html) |
   | :jitter            | Random jitter applied to each action time             | [Duration](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html) |
   | :skip-at           | Calendar specs to skip                                | List of [ScheduleCalendarSpec](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/schedules/ScheduleCalendarSpec.html) |
   | :timezone          | IANA timezone name (e.g. `US/Central`, `UTC`); one of [`ZoneId/getAvailableZoneIds`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/ZoneId.html#getAvailableZoneIds()) | String |

   `:action` options:

   | Value          | Description                                                         | Type |
   |----------------|---------------------------------------------------------------------|------|
   | :workflow-type | The workflow to start                                               | A [[temporal.workflow/defworkflow]] reference or its String name |
   | :arguments     | Arguments passed to the started workflow                            | Serializable value |
   | :options       | Workflow options for each started run                               | See [[temporal.client.core/create-workflow]] |

   `:search-attributes` is supported in both `:schedule` and `:action :options`.
   Both simple and typed formats are accepted; see [Search attribute input formats](/doc/workflows.md#search-attribute-input-formats).

   ```clojure
   (defworkflow my-workflow
     [args]
     ...)

   (let [client (create-client {:target \"localhost:7233\"})]
     (schedule
      client
      \"my-workflow-schedule\"
      {:schedule {:trigger-immediately? false
                  :search-attributes {\"ScheduleType\" {:type :keyword :value \"hourly\"}}}
       :state {:paused? false}
       :policy {:pause-on-failure? true}
       :spec {:cron-expressions [\"0 * * * *\"]}
       :action {:workflow-type my-workflow
                :arguments {:value 1}
                :options {:workflow-id \"my-workflow\"
                          :task-queue \"my-task-queue\"
                          :search-attributes {\"WorkflowType\" {:type :keyword :value \"report\"}}}}}))
   ```"
  [^ScheduleClient client schedule-id options]
  (let [schedule (s/schedule-> options)
        schedule-options (s/schedule-options-> (:schedule options))]
    (log/tracef "create schedule:" schedule-id)
    (.createSchedule client schedule-id schedule schedule-options)))

(defn unschedule
  "Deletes a Temporal `Schedule` via a schedule-id

    ```clojure

   (let [client (create-client {:target \"localhost:7233\"})]
      (unschedule client \"my-schedule\")
    ```"
  [^ScheduleClient client schedule-id]
  (log/tracef "remove schedule:" schedule-id)
  (-> client
      (.getHandle schedule-id)
      (.delete)))

(defn describe
  "Describes an existing Temporal `Schedule` via a schedule-id

   ```clojure

   (let [client (create-client {:target \"localhost:7233\"})]
      (describe client \"my-schedule\")
    ```"
  [^ScheduleClient client schedule-id]
  (-> client
      (.getHandle schedule-id)
      (.describe)))

(defn pause
  "Pauses an existing Temporal `Schedule` via a schedule-id

   ```clojure

   (let [client (create-client {:target \"localhost:7233\"})]
      (pause client \"my-schedule\")
    ```"
  [^ScheduleClient client schedule-id]
  (log/tracef "pausing schedule:" schedule-id)
  (-> client
      (.getHandle schedule-id)
      (.pause)))

(defn unpause
  "Unpauses an existing Temporal `Schedule` via a schedule-id

   ```clojure

   (let [client (create-client {:target \"localhost:7233\"})]
      (unpause client \"my-schedule\")
    ```"
  [^ScheduleClient client schedule-id]
  (log/tracef "unpausing schedule:" schedule-id)
  (-> client
      (.getHandle schedule-id)
      (.unpause)))

(defn execute
  "Runs a Temporal Schedule workflow execution immediately via a schedule-id

   ```clojure

   (let [client (create-client {:target \"localhost:7233\"})]
      (execute client \"my-schedule\" :skip)
    ```"
  [^ScheduleClient client schedule-id overlap-policy]
  (log/tracef "execute schedule:" schedule-id)
  (-> client
      (.getHandle schedule-id)
      (.trigger (s/overlap-policy-> overlap-policy))))

(defn reschedule
  "Updates the current Temporal `Schedule` via a schedule-id.
   Uses the same options as [[schedule]] except `:schedule`.

   The `ScheduleHandle` takes a unary function object
   of the signature:

   (ScheduleUpdateInput) -> ScheduleUpdate

   ```clojure

   (let [client (create-client {:target \"localhost:7233\"})]
      (reschedule client \"my-schedule\" {:spec {:cron-expressions [\"1 * * * *\"]}})
   ```"
  [^ScheduleClient client schedule-id options]
  (log/tracef "update schedule:" schedule-id)
  (letfn [(update-fn
            [opts ^ScheduleUpdateInput input]
            (let [schedule (-> input
                               (.getDescription)
                               (.getSchedule))]
              (-> schedule
                  (s/schedule-> opts)
                  (ScheduleUpdate.))))]
    (-> client
        (.getHandle schedule-id)
        (.update (u/->Func (partial update-fn options))))))
