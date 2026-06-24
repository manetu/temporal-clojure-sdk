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

### Connecting to Temporal Cloud with an API Key

Temporal Cloud uses API keys for authentication. Pass a 0-arity function that returns the key string via `:api-key-fn`:

```clojure
(create-client {:target "your-namespace.tmprl.cloud:7233"
                :namespace "your-namespace"
                :api-key-fn (constantly "your-api-key")})
```

**Since Temporal Java SDK 1.33, providing `:api-key-fn` automatically enables TLS** — you do not need to also set `:enable-https true`. This auto-enable only applies when neither `:ssl-context` nor `:channel` is also provided.

If you need to explicitly control TLS behavior:

```clojure
;; Explicitly disable TLS even though an API key is set (unusual; e.g. local dev proxy)
(create-client {:target "localhost:7233"
                :namespace "default"
                :api-key-fn (constantly "dev-key")
                :enable-https false})

;; mTLS + API key: ssl-context takes precedence; TLS is not auto-enabled
(require '[temporal.tls :as tls])
(create-client {:target "your-namespace.tmprl.cloud:7233"
                :namespace "your-namespace"
                :api-key-fn (constantly "your-api-key")
                :ssl-context (tls/new-ssl-context {:cert-path "client.crt"
                                                   :key-path  "client.key"})})
```

### gRPC Transport Compression

Since Temporal Java SDK 1.36, **GZIP compression is enabled by default** on all gRPC calls. This is transparent to most users but may make traffic harder to inspect with tools like Wireshark or grpcurl.

To disable compression (e.g. for debugging or environments where compression adds overhead):

```clojure
(import '[io.temporal.serviceclient GrpcCompression])

(create-client {:target "temporal-frontend:7233"
                :namespace "my-namespace"
                :grpc-compression GrpcCompression/NONE})
```

The `:grpc-compression` option accepts any `GrpcCompression` enum value (`GrpcCompression/NONE` or `GrpcCompression/GZIP`).

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

### Plugins

Since Temporal Java SDK 1.33, a stable Plugin API allows customizing SDK behavior at a coarser-grained level than interceptors.  Pass a collection of plugin instances via `:plugins` in the relevant options map.

**WorkflowClient plugin** (`temporal.client.core/create-client` via `:workflow-client-options`):

```clojure
(import '[io.temporal.client WorkflowClientPlugin])

(def my-client-plugin
  (reify WorkflowClientPlugin
    (getName [_] "my-client-plugin")
    (configureWorkflowClient [_ builder]
      ;; inspect or mutate the WorkflowClientOptions$Builder
      )))

(c/create-client {:plugins [my-client-plugin]})
```

**ScheduleClient plugin** (`temporal.client.schedule/create-client`):

```clojure
(import '[io.temporal.client.schedules ScheduleClientPlugin])

(def my-schedule-plugin
  (reify ScheduleClientPlugin
    (getName [_] "my-schedule-plugin")
    (configureScheduleClient [_ builder])))

(s/create-client {:plugins [my-schedule-plugin]})
```

**gRPC stubs plugin** (`:stub-options` inside `create-client`):

```clojure
(import '[io.temporal.serviceclient WorkflowServiceStubsPlugin])

(def my-stubs-plugin
  (reify WorkflowServiceStubsPlugin
    (getName [_] "my-stubs-plugin")
    (configureServiceStubs [_ builder])
    (connectServiceClient [_ opts supplier] (.get supplier))))

(c/create-client {:stub-options {:plugins [my-stubs-plugin]}})
```

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
                                             :memo {"Created by" "John Doe"}}
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
