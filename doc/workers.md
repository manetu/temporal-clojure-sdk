# Workers

## What is a Worker?

A Worker is a service that executes Workflows and Activities.  Workers are defined and executed on user-controlled hosts.  You can use [temporal.client.worker/start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.worker#start) to create and run as many Workers as your use case demands, across any number of hosts.

Workers poll Task Queues for Tasks, execute chunks of code in response to those Tasks, and then communicate the results back to the Temporal Server.

As a developer, running Workers is a reasonably simple procedure because this Clojure SDK handles all the communication between the Worker and the Temporal Server behind the scenes.

## How to start a Worker

1. Create a Workflow Client with [temporal.client.core/create-client](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#create-client)
2. Create a Worker instance with [temporal.client.worker/start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.worker#start)

As a simple example, let's say we want our Worker to be able to execute the following Workflow implementation:

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

We can register our Worker as follows:

```clojure
(require '[temporal.client.core :as c])
(require '[temporal.client.worker :as worker])

(def task-queue "MyTaskQueue")

(let [client (c/create-client)]
  (worker/start client {:task-queue task-queue}))
```

All visible defworkflow/defactivity functions are registered automatically, by default.  If you want greater control, such as to split specific Workflows across TaskQueues, you may use the `:dispatch` option.

```clojure
(worker/start client {:task-queue task-queue
                      :dispatch {:activities [greet-activity] :workflows [greet-workflow]}})
```

The Temporal Server adds a new task to the Workflows / Activity Task Queue whenever you start a Workflow or when a Workflow needs to invoke an Activity.  Any Worker polling that Task Queue that has the requested Workflow / Activity registered can pick up the new task and execute it.

## Automatic Poller Scaling

By default, Workers use a fixed number of pollers for each task type.  You can configure Workers to automatically scale the number of concurrent polls based on load using the poller behavior options:

```clojure
(worker/start client {:task-queue task-queue
                      :workflow-task-pollers-behavior :autoscaling
                      :activity-task-pollers-behavior :autoscaling
                      :nexus-task-pollers-behavior :autoscaling})
```

When autoscaling is enabled, you can expect:
- Fewer unnecessary polls during low load
- Increased polls during high load for better throughput

You can also customize the min/max bounds:

```clojure
(worker/start client {:task-queue task-queue
                      :activity-task-pollers-behavior {:type :autoscaling
                                                       :min-pollers 2
                                                       :max-pollers 10}})
```
