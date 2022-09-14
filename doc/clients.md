# Temporal Client

## Overview

To initialize a Workflow Client, create an instance of a Workflow client with ([create-client](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#create-client)), create a Workflow stub with ([create-workflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#create-workflow)), and invoke the Workflow with ([start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#start))).  Finally, gather the results of the Workflow with ([get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#get-result)).

## Details

To start a Workflow Execution, your Temporal Server must be running, and your front-end service must be accepting gRPC calls.

You can provide options to (create-client) to establish a connection to the specifics of the Temporal front-end service in your environment.

```clojure
(create-client {:target "temporal-frontend:7233" :namespace "my-namespace"})
```

After establishing a successful connection to the Temporal Frontend Service, you may perform operations such as:

- **Starting Workflows**: See ([start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#start))) and ([signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#signal-with-start))
- **Signaling Workflows**: See ([>!](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#%3E!)) and ([signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#signal-with-start))
- **Gathering Results**: See ([get-result](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#get-result))
- **Cancelling Workflows**: See ([cancel](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#cancel)) and ([terminate](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#terminate))

As a simple example, for the following Workflow implementation:

```clojure
(require '[temporal.workflow :refer [defworkflow]])
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow greeter-workflow
  [ctx {:keys [args]}]
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
