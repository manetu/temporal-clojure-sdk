# Updates Sample

Demonstrates workflow updates for synchronous request-response patterns.

## What This Sample Shows

- Registering update handlers with `register-update-handler!`
- Using validators to reject invalid update requests
- Sending synchronous updates with `update`
- Using async updates with `start-update`
- Atomically starting workflows with updates using `update-with-start`

## Updates vs Signals vs Queries

| Feature | Signals | Queries | Updates |
|---------|---------|---------|---------|
| Modify state | Yes | No | Yes |
| Return value | No | Yes | Yes |
| Validation | No | N/A | Yes |
| Blocking ops | No | No | Yes |

Updates combine the best of signals and queries: they can modify workflow state AND return values to the caller.

## Prerequisites

A running Temporal server:

```bash
temporal server start-dev
```

## Running the Sample

```bash
cd samples/updates
lein run
```

## Examples Demonstrated

### 1. Synchronous Updates

Send an update and wait for the result:

```clojure
(let [result (c/update workflow :increment {:amount 5})]
  (println "New counter value:" result))
```

### 2. Async Updates

Start an update without blocking, then retrieve the result later:

```clojure
(let [handle (c/start-update workflow :increment {:amount 25})]
  ;; Do other work...
  (let [result @(:result handle)]
    (println "Result:" result)))
```

### 3. Validator Rejection

Validators run before the update handler and can reject invalid requests:

```clojure
(w/register-update-handler!
  (fn [update-type args] ...)
  {:validator (fn [update-type args]
                (when (neg? (:amount args))
                  (throw (IllegalArgumentException. "amount must be positive"))))})
```

### 4. Update-with-Start

Atomically start a workflow (if not running) and send an update:

```clojure
(let [workflow (c/create-workflow client my-workflow
                {:workflow-id-conflict-policy :use-existing})
      handle (c/update-with-start workflow :increment {:amount 50} {:initial 0})]
  @(:result handle))
```

## Key Concepts

### Update Handler

```clojure
(w/register-update-handler!
  (fn [update-type args]
    (case update-type
      :increment (swap! state update :counter + (:amount args))
      :get-value (:counter @state)))
  {:validator (fn [update-type args]
                ;; Throw to reject
                )})
```

### Sending Updates

```clojure
;; Synchronous - blocks until complete
(c/update workflow :increment {:amount 5})

;; Async - returns immediately
(let [handle (c/start-update workflow :increment {:amount 5})]
  @(:result handle))
```

## Use Cases

- **Counters/accumulators**: Increment values and get the new total
- **State machines**: Transition states and confirm the new state
- **Reservations**: Reserve resources and get confirmation
- **Validating mutations**: Reject invalid state changes at the edge
