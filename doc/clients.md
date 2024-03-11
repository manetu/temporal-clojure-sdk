# Temporal Client

## Overview

To initialize a Workflow Client, create an instance of a Workflow client with [temporal.client.core/create-client](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#create-client), create a Workflow stub with [temporal.client.core/create-workflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#create-workflow), and invoke the Workflow with [temporal.client.core/start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#start)).  Finally, gather the results of the Workflow with [temporal.client.core/get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#get-result).

To initialize a Schedule Client, create an instance of a Schedule client with [temporal.client.schedule/create-client](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule#create-client), create a Schedule with [temporal.client.schedule/schedule](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule#schedule), and run a scheduled workflow execution immediately with [temporal.client.schedule/execute](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule#execute)).

## Details

Using the namespace `temporal.client.core` or `temporal.client.schedule`

To start a Workflow Execution or manage a Scheduled Workflow Execution, your Temporal Server must be running, and your front-end service must be accepting gRPC calls.

You can provide options to (create-client) to establish a connection to the specifics of the Temporal front-end service in your environment.

```clojure
(create-client {:target "temporal-frontend:7233" :namespace "my-namespace"})
```

### Workflow Executions

After establishing a successful connection to the Temporal Frontend Service, you may perform Workflow Execution specific operations such as:

- **Starting Workflows**: See [temporal.client.core/start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#start) and [temporal.client.core/signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#signal-with-start)
- **Signaling Workflows**: See [temporal.client.core/>!](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#%3E!) and [temporal.client.core/signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#signal-with-start)
- **Gathering Results**: See [temporal.client.core/get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#get-result)
- **Cancelling Workflows**: See [temporal.client.core/cancel](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#cancel) and [temporal.client.core/terminate](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#terminate)

As a simple example, for the following Workflow implementation:

```clojure
(require '[temporal.workflow :refer [defworkflow]])
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow greeter-workflow
  [args]
  (log/info "greeter-workflow:" args)
  @(a/invoke greet-activity args))
```

We can create a client to invoke our Workflow as follows:

```clojure
(require '[temporal.client.core :as c])

(def task-queue "MyTaskQueue")

(let [client (c/create-client)
      workflow (c/create-workflow client greeter-workflow {:task-queue task-queue})]
  (c/start workflow {:name "Bob"})
  (println @(c/get-result workflow)))
```

Evaluating this code should result in `Hi, Bob` appearing on the console.  Note that (get-result) returns a promise, thus requiring a dereference.

### Scheduled Workflow Executions

After establishing a successful connection to the Temporal Frontend Service, you may perform Schedule specific operations such as:

- **Creating Schedules**: See [temporal.client.schedule/schedule](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule/schedule)
- **Describing Schedules**: See [temporal.client.schedule/describe](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule/describe)
- **Pausing Schedules**: See [temporal.client.schedule/pause](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule/pause)
- **Unpausing Schedules**:  See [temporal.client.schedule/unpause](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule/unpause)
- **Removing Schedules**:  See [temporal.client.schedule/unschedule](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule/unschedule)
- **Triggering Schedules**: See [temporal.client.schedule/execute](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule/execute)
- **Updating Schedules**: See [temporal.client.schedule/reschedule](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.schedule/reschedule)

Schedules can be built with cron expressions or built in Temporal time types.

Schedules can executed immediately via "triggering" them.

Schedules will handle overlapping runs determined by the `SchedulePolicy` you give it when creating it.

Example Usage

As a simple example, for the following Workflow implementation:

```clojure
(require '[temporal.workflow :refer [defworkflow]])
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow greeter-workflow
  [args]
  (log/info "greeter-workflow:" args)
  @(a/invoke greet-activity args))
```

Create and manage a schedule as follows


```clojure
(require '[temporal.client.schedule :as s])
(let [client-options {:target "localhost:7233"
                      :namespace "default"
                      :enable-https false}
      task-queue "MyTaskQueue"
      workflow-id "my-workflow"
      schedule-id "my-schedule"
      client (s/create-client client-options)]
  (s/schedule client schedule-id {:schedule {:trigger-immediately? true
                                             :memo "Created by John Doe"}
                                  :spec {:cron-expressions ["0 0 * * *"]
                                         :timezone "US/Central"}
                                  :policy {:pause-on-failure? false
                                           :catchup-window (Duration/ofSeconds 10)
                                         :overlap :skip}
                                  :action {:arguments {:name "John"}
                                           :options {:workflow-id workflow-id
                                                     :task-queue task-queue}
                                           :workflow-type greeter-workflow}})
  (s/describe client schedule-id)
  (s/pause client schedule-id)
  (s/unpause client schedule-id)
  (s/execute client schedule-id :skip)
  (s/reschedule client schedule-id {:spec {:cron-expressions ["0 1 * * *"]}})
  (s/unschedule client schedule-id))
```