# Signals and Queries Sample

Demonstrates how to use signals and queries for workflow communication in Temporal.

## What This Sample Shows

- Creating signal channels with `create-signal-chan`
- Receiving signals with blocking `<!` (with timeout)
- Registering query handlers with `register-query-handler!`
- Sending signals to running workflows with `>!`
- Querying workflow state with `query`

## Prerequisites

A running Temporal server:

```bash
temporal server start-dev
```

## Running the Sample

```bash
cd samples/signals-queries
lein run
```

## Expected Output

```
Starting Signals and Queries sample...
Starting order workflow...
Waiting for signal... Current status: :pending
===================================
Querying order status...
Current status: :pending
===================================
Sending signal: add-note
Received signal: :add-note
Waiting for signal... Current status: :pending
Querying full order state...
Order state: {:order-id "12345", :items [...], :status :pending, :notes ["Customer requested gift wrapping"]}
===================================
Sending signal: ship
Received signal: :ship
Waiting for signal... Current status: :shipped
Status after shipping: :shipped
===================================
Sending signal: complete
Received signal: :complete
Order workflow completed with status: :completed
===================================
Final order state: {:order-id "12345", :status :completed, ...}
===================================
Sample completed.
```

## Key Concepts

### Signal Channels

Signal channels provide a core.async-inspired way to receive signals:

```clojure
(let [signals (s/create-signal-chan)]
  ;; Block until a signal arrives (or timeout)
  (when-let [msg (s/<! signals :my-signal (Duration/ofSeconds 30))]
    (process msg)))
```

### Query Handlers

Query handlers let clients inspect workflow state without modifying it:

```clojure
(w/register-query-handler!
  (fn [query-type args]
    (case query-type
      :get-status (:status @state)
      :get-all @state
      nil)))
```

### Sending Signals

From a client, send signals to running workflows:

```clojure
(c/>! workflow :signal-name {:data "value"})
```

### Querying Workflows

Query a workflow's state:

```clojure
(c/query workflow :get-status {})
```

## Use Cases

- **Order processing**: Update order status, add notes, query current state
- **Long-running jobs**: Check progress, cancel or pause execution
- **Stateful services**: Maintain and expose service state
