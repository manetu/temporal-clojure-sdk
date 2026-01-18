# Hello World Sample

A simple example demonstrating basic Temporal workflow and activity usage in Clojure.

## What This Sample Shows

- Defining an activity with `defactivity`
- Defining a workflow with `defworkflow`
- Invoking an activity from within a workflow
- Creating a Temporal client and worker
- Starting a workflow and retrieving its result

## Prerequisites

A running Temporal server. The easiest way is to use the Temporal CLI:

```bash
temporal server start-dev
```

## Running the Sample

```bash
cd samples/hello-world
lein run
```

Or with a custom name:

```bash
lein run Alice
```

## Expected Output

```
Starting Hello World sample...
Starting workflow with name: World
Creating greeting for: World
Workflow completed with greeting: Hello, World!
===================================
Workflow result: Hello, World!
===================================
Sample completed.
```

## Code Structure

- `core.clj` - Defines the workflow and activity
- `main.clj` - Entry point that sets up the client, worker, and runs the workflow

## Key Concepts

### Activities

Activities are where you perform side effects (API calls, database access, etc.). They are defined with `defactivity` and receive a context map and arguments.

```clojure
(defactivity greet-activity
  [ctx {:keys [name]}]
  (str "Hello, " name "!"))
```

### Workflows

Workflows orchestrate activities and must be deterministic. They are defined with `defworkflow` and invoke activities using `temporal.activity/invoke`.

```clojure
(defworkflow hello-workflow
  [{:keys [name]}]
  @(a/invoke greet-activity {:name name} {:start-to-close-timeout (Duration/ofSeconds 10)}))
```

### Client and Worker

The client connects to the Temporal server, while the worker polls for and executes workflow and activity tasks.

```clojure
(let [client (c/create-client {})
      worker (worker/start client {:task-queue "my-queue"})]
  ;; ... use client to start workflows ...
  (worker/stop worker))
```
