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

## Hot reloading during development

By default, Workers capture the current function values when they start. For REPL-driven development, you can opt in to resolving Vars at execution time so re-evaluated `defactivity` and `defworkflow` forms can be picked up without restarting the Worker.

```clojure
(worker/start client {:task-queue task-queue
                      :hot-reload-activities? true})
```

Activity hot reloading is usually the safest development option. Workflow hot reloading is also available, but should be used cautiously because Temporal workflows must remain deterministic during replay:

```clojure
(worker/start client {:task-queue task-queue
                      :hot-reload-activities? true
                      :hot-reload-workflows? true})
```

These options default to `false` and are intended for development / REPL use, not production deployments.

## OpenTracing Integration

Workers and clients support distributed tracing via the `temporal-opentracing` library.  Wire up the client and worker interceptors using `OpenTracingOptions` to inject a tracer:

```clojure
(require '[temporal.client.core :as c])
(require '[temporal.client.worker :as worker])
(:import [io.temporal.opentracing OpenTracingClientInterceptor OpenTracingOptions OpenTracingWorkerInterceptor])

(def task-queue "MyTaskQueue")

(let [opts   (-> (OpenTracingOptions/newBuilder)
                 (.setTracer my-tracer)  ;; e.g. a Jaeger or Zipkin tracer
                 (.build))
      client (c/create-client {:interceptors [(OpenTracingClientInterceptor. opts)]})]
  (worker/start client {:task-queue task-queue
                        :worker-interceptors [(OpenTracingWorkerInterceptor. opts)]}))
```

When `OpenTracingOptions` is omitted, the interceptors fall back to `GlobalTracer.get()`.  Using the options builder is preferred for testability — pass a `MockTracer` in tests to verify that spans are captured.

As of Temporal Java SDK 1.36, a new `OpenTracingActivityClientInterceptor` is available for tracing standalone activities executed via `ActivityClient`.  This will be wired in when `temporal.client.activity` (standalone activity support) is added to the SDK.

## Plugins

Since Temporal Java SDK 1.33, a stable Plugin API allows customizing worker behavior at a coarser-grained level than interceptors.  Pass a collection of plugin instances via `:plugins` in the `factory-options` argument to `worker/start`.

```clojure
(import '[io.temporal.worker WorkerPlugin])

(def my-worker-plugin
  (reify WorkerPlugin
    (getName [_] "my-worker-plugin")
    (configureWorkerFactory [_ builder])
    (configureWorker [_ task-queue builder])
    (initializeWorker [_ task-queue worker])
    (startWorker [_ task-queue worker next] (.accept next task-queue worker))
    (shutdownWorker [_ task-queue worker next] (.accept next task-queue worker))
    (startWorkerFactory [_ factory next] (.accept next factory))
    (shutdownWorkerFactory [_ factory next] (.accept next factory))
    (replayWorkflowExecution [_ worker history cb] (.replay cb worker history))))

(worker/start client {:task-queue task-queue}
                     {:plugins [my-worker-plugin]})
```

Plugins and interceptors are complementary: interceptors hook into individual workflow/activity calls, while plugins hook into the lifecycle of the worker itself.

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

## Shutdown Options

### Activity heartbeats during shutdown

By default, Temporal blocks activity heartbeats once graceful shutdown begins.  Workers use this to detect shutdown and throw `ActivityWorkerShutdownException` from the heartbeat call so activities can react promptly.

Setting `:allow-activity-heartbeat-during-shutdown true` removes that restriction — heartbeats continue to flow until the activity finishes.  The trade-off is that `ActivityWorkerShutdownException` becomes undetectable: activities that rely on it to exit early will not see the exception and must finish naturally.

```clojure
(worker/start client {:task-queue task-queue
                      :allow-activity-heartbeat-during-shutdown true})
```

### Shutdown check interval (test-only)

`:shutdown-check-interval` controls how often the worker factory polls to check whether all in-flight tasks have drained during graceful shutdown.  The default (50 ms) is appropriate for production.

Temporal **discourages changing this value in production**.  It is exposed here specifically for unit tests that need worker shutdown to complete quickly:

```clojure
(require '[java.time Duration])

;; In a test helper — not for production use
(worker/start client {:task-queue task-queue}
                     {:shutdown-check-interval (Duration/ofMillis 10)})
```
