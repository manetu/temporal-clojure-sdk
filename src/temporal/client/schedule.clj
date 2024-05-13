(ns temporal.client.schedule
  (:require [taoensso.timbre :as log]
            [temporal.client.options :as copts]
            [temporal.internal.utils :as u]
            [temporal.internal.schedule :as s])
  (:import [java.time Duration]
           [io.temporal.client.schedules ScheduleClient ScheduleUpdate ScheduleUpdateInput]))

(set! *warn-on-reflection* true)

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

   - `client`: [ScheduleClient]https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/schedules/ScheduleClient.html
   - `schedule-id`: The string name of the schedule in Temporal, keeping it consistent with workflow id is a good idea
   - `options`: A map containing the `:schedule`, `:state`, `:policy`, `:spec`, and `:action` option maps for the `Schedule`

   ```clojure
   (defworkflow my-workflow
     [ctx args]
     ...)

   (let [client (create-client {:target \"localhost:8080\"})]
   (create
      client
      \"my-workflow\"
      {:schedule {:trigger-immediately? false}
       :state {:paused? false}
       :policy {:pause-on-failure? true}
       :spec {:crons [\"0 * * * *\"]}
       :action {:workflow-type my-workflow
                :arguments {:value 1}
                :options {:workflow-id \"my-workflow\"}}}))
   ```"
  [^ScheduleClient client schedule-id options]
  (let [schedule (s/schedule-> options)
        schedule-options (s/schedule-options-> (:schedule options))]
    (log/tracef "create schedule:" schedule-id)
    (.createSchedule client schedule-id schedule schedule-options)))

(defn unschedule
  "Deletes a Temporal `Schedule` via a schedule-id

    ```clojure

   (let [client (create-client {:target \"localhost:8080\"})]
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

   (let [client (create-client {:target \"localhost:8080\"})]
      (describe client \"my-schedule\")
    ```"
  [^ScheduleClient client schedule-id]
  (-> client
      (.getHandle schedule-id)
      (.describe)))

(defn pause
  "Pauses an existing Temporal `Schedule` via a schedule-id

   ```clojure

   (let [client (create-client {:target \"localhost:8080\"})]
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

   (let [client (create-client {:target \"localhost:8080\"})]
      (unpause client \"my-schedule\")
    ```"
  [^ScheduleClient client schedule-id]
  (log/tracef "unpausing schedule:" schedule-id)
  (-> client
      (.getHandle schedule-id)
      (.unpause)))

(defn execute
  "Runs a Temporal `Schedule's` workflow execution immediately via a schedule-id

   ```clojure

   (let [client (create-client {:target \"localhost:8080\"})]
      (execute client \"my-schedule\" :skip)
    ```"
  [^ScheduleClient client schedule-id overlap-policy]
  (log/tracef "execute schedule:" schedule-id)
  (-> client
      (.getHandle schedule-id)
      (.trigger (s/overlap-policy-> overlap-policy))))

(defn reschedule
  "Updates the current Temporal `Schedule` via a schedule-id.
   Uses the same options as create asside from `:schedule`

   The `ScheduleHandle` takes a unary function object
   of the signature:

   (ScheduleUpdateInput) -> ScheduleUpdate

   ```clojure

   (let [client (create-client {:target \"localhost:8080\"})]
      (reschedule client \"my-schedule\" {:spec {:crons [\"1 * * * *\"]}})
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
