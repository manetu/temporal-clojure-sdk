(ns temporal.client.schedule
  (:require [taoensso.timbre :as log]
            [temporal.client.options :as copts]
            [temporal.internal.grpc :as g]
            [temporal.internal.utils :as u]
            [temporal.internal.schedule :as s])
  (:import [java.time Duration]
           [io.temporal.client.schedules ScheduleClient ScheduleUpdate ScheduleUpdateInput]))

(set! *warn-on-reflection* true)

(defn create-client
  "Creates a `ScheduleClient` instance suitable for interacting with Temporal's Schedules.
   The `ScheduleClient` has slightly less options then the `WorkflowClient` but it is mostly the same otherwise.

   Arguments:

   - `options`: Client configuration option map (See below)
   - `timeout`: Connection timeout as a [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) (default: 5s)

    #### options map


   | Value                     | Description                                                                 | Type         | Default |
   | ------------------------- | --------------------------------------------------------------------------- | ------------ | ------- |
   | :target                   | Sets the connection host:port                                               | String       | \"127.0.0.1:7233\" |
   | :identity                 | Overrides the worker node identity (workers only)                           | String       | |
   | :namespace                | Sets the Temporal namespace context for this client                         | String       | |
   | :data-converter           | Overrides the data converter used to serialize arguments and results.       | [DataConverter](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/converter/DataConverter.html) | |
   | :channel                  | Sets gRPC channel to use. Exclusive with target and sslContext              | [ManagedChannel](https://grpc.github.io/grpc-java/javadoc/io/grpc/ManagedChannel.html) | |
   | :ssl-context              | Sets gRPC SSL Context to use (See [[temporal.tls/new-ssl-context]])         | [SslContext](https://netty.io/4.0/api/io/netty/handler/ssl/SslContext.html) | |
   | :enable-https             | Sets option to enable SSL/TLS/HTTPS for gRPC                                | boolean      | false |
   | :rpc-timeout              | Sets the rpc timeout value for non query and non long poll calls            | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 10s |
   | :rpc-long-poll-timeout    | Sets the rpc timeout value                                                  | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 60s |
   | :rpc-query-timeout        | Sets the rpc timeout for queries                                            | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 10s |
   | :backoff-reset-freq       | Sets frequency at which gRPC connection backoff should be reset practically | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 10s |
   | :grpc-reconnect-freq      | Sets frequency at which gRPC channel will be moved into an idle state       | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 60s |
   | :headers                  | Set the headers                                                             | [Metadata](https://grpc.github.io/grpc-java/javadoc/io/grpc/Metadata.html) | |
   | :enable-keepalive         | Set keep alive ping from client to the server                               | boolean       | false |
   | :keepalive-time           | Set the keep alive time                                                     | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
   | :keepalive-timeout        | Set the keep alive timeout                                                  | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
   | :keepalive-without-stream | Set if client sends keepalive pings even with no active RPCs                | boolean       | false |
   | :metrics-scope            | The scope to be used for metrics reporting                                  | [Scope](https://github.com/uber-java/tally/blob/master/core/src/main/java/com/uber/m3/tally/Scope.java) | |"
  ([options] (create-client options (Duration/ofSeconds 5)))
  ([options timeout]
   (let [service (g/service-stub-> options timeout)]
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
        schedule-options (s/schedule-options-> options)]
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
