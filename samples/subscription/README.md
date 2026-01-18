# Subscription Sample

Demonstrates long-running workflows with continue-as-new.

## What This Sample Shows

- Building a subscription/polling workflow
- Using `sleep` for periodic execution
- Using `continue-as-new` to prevent unbounded history growth
- Handling cancellation signals for graceful shutdown

## The Problem

Long-running workflows accumulate event history over time. This history is used for replay during recovery, but unbounded growth can cause performance issues.

## The Solution

Use `continue-as-new` to complete the current workflow execution and immediately start a new one with fresh history, passing along any necessary state.

```
Workflow Run 1 (iterations 0-4)
        ↓ continue-as-new
Workflow Run 2 (iterations 5-9)
        ↓ continue-as-new
Workflow Run 3 (iterations 10-14)
        ↓ ...
```

## Prerequisites

A running Temporal server:

```bash
temporal server start-dev
```

## Running the Sample

```bash
cd samples/subscription
lein run
```

The workflow will run for about 15 seconds, demonstrating several polling iterations and at least one continue-as-new transition.

## Expected Output

```
Starting Subscription sample...

This sample runs a subscription workflow that:
  1. Polls for updates every 2 seconds
  2. Processes any updates found
  3. Uses continue-as-new every 5 iterations
  4. Can be cancelled gracefully via signal

Subscription workflow iteration 0 for SUB-12345 - total processed: 0
Sleeping for 2 seconds...
Checking for updates for subscription: SUB-12345
Found 1 updates to process
Processing update for SUB-12345 ...
Sending notification: 1 updates processed
Subscription workflow iteration 1 for SUB-12345 - total processed: 1
...
Reached 5 iterations, continuing as new workflow...
Subscription workflow iteration 0 for SUB-12345 - total processed: 3
...
Sending cancel signal...
Received cancel signal, shutting down gracefully...
Subscription workflow result: {:status :cancelled, :total-processed 5, ...}
```

## Key Concepts

### Periodic Execution with Sleep

```clojure
(w/sleep (Duration/ofSeconds 60))  ;; Sleep for 1 minute between polls
```

### Continue-As-New

When you've accumulated enough history, restart with fresh state:

```clojure
(when (>= iteration max-iterations)
  (w/continue-as-new {:subscription-id id
                      :iteration 0
                      :total-processed total}))
```

### Graceful Cancellation

Allow the workflow to be cancelled mid-sleep:

```clojure
(let [cancelled? (s/<! signals ::cancel poll-interval)]
  (if cancelled?
    {:status :cancelled}
    ;; continue processing...
    ))
```

### Passing State Through Continue-As-New

State that needs to persist across runs is passed as arguments:

```clojure
(w/continue-as-new {:iteration (inc iteration)
                    :total-processed (+ total new-count)
                    :last-check-time now})
```

## Use Cases

- **Email digests**: Collect and send periodic summaries
- **Data sync**: Periodically sync data between systems
- **Monitoring**: Poll external services for health/status
- **Queue processing**: Process items from a queue over time
- **Subscription billing**: Handle recurring billing cycles
