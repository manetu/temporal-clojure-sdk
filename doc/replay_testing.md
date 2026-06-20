# Replay Testing

## Why replay testing?

Temporal workflows must be **deterministic**: replaying a workflow's persisted Event History against its code must produce the identical command sequence. Any change that reorders activities, adds or removes steps without versioning, branches on non-deterministic inputs (wall-clock time, random values, external state), or upgrades a library that changes internal behavior will **break replay** — silently, until a worker tries to resume an in-flight workflow in production.

The standard mitigation is the **capture-then-replay** pattern:

1. Capture representative production (or test-environment) histories as JSON fixtures.
2. In CI, replay those fixtures against the current code.
3. Fail the build on any mismatch.

The Temporal Java SDK ships `WorkflowReplayer` and `WorkflowExecutionHistory` for exactly this purpose. This SDK exposes them as idiomatic Clojure through two namespaces: `temporal.testing.history` and `temporal.testing.replayer`.

For background, see the [Temporal Testing Suite](https://docs.temporal.io/develop/java/testing-suite) and [Event History Walkthrough](https://docs.temporal.io/encyclopedia/event-history) in the upstream docs.

---

## The capture-then-replay workflow

```
┌─────────────────────────────────────────────────────────────┐
│  1. Run workflow in test env (or production)                 │
│  2. Capture history with history/fetch or history/->json     │
│  3. Save JSON fixture to test/resources/histories/           │
│  4. In CI: replay fixture with replayer/replay-history       │
│             or replayer/replay-all for batches               │
│  5. Fail build if replay throws                              │
└─────────────────────────────────────────────────────────────┘
```

The two namespaces map cleanly onto these phases:

| Namespace | Role |
|-----------|------|
| `temporal.testing.history` | Load, capture, and serialize event histories |
| `temporal.testing.replayer` | Replay histories against current workflow code |

---

## Loading and capturing histories

Require both namespaces:

```clojure
(require '[temporal.testing.history :as history]
         '[temporal.testing.replayer :as replayer])
```

### From a classpath resource (most common in CI)

Store your history fixtures under `test/resources/histories/` so they land on the test classpath:

```clojure
(history/from-resource "histories/order-workflow.json")
```

Throws a clear `ex-info` with `{:resource name}` if the file is absent.

### From a file path

```clojure
(history/from-file "test/resources/histories/order-workflow.json")
;; also accepts java.io.File and java.nio.file.Path
```

### From a raw JSON string

```clojure
(history/from-json (slurp "order.json"))
;; optional 2-arity form overrides the embedded workflow ID
(history/from-json json "my-workflow-id")
```

### Capturing a history from a live client

After running a workflow in a `temporal.testing.env` or against a real server, fetch the history and save it:

```clojure
(let [h (history/fetch client "workflow-id-123")]
  (spit "test/resources/histories/order.json" (history/->json h)))
```

`->json` defaults to pretty-printed output. Pass `false` for compact:

```clojure
(history/->json h false)
```

### Inspecting histories at the REPL

Every `WorkflowExecutionHistory` implements `clojure.core.protocols/Datafiable`:

```clojure
(require '[clojure.datafy :as d])

(d/datafy (history/from-resource "histories/order-workflow.json"))
;; => {:workflow-id "order-123"
;;     :run-id      "abc-..."
;;     :events      [#object[...] ...]}
```

Full API: [temporal.testing.history](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.history)

---

## Replaying a history

### One-shot replay

The simplest form — no setup required:

```clojure
(replayer/replay-history (history/from-resource "histories/order-workflow.json"))
```

With a custom data converter:

```clojure
(replayer/replay-history h {:data-converter my-converter})
```

Throws `ex-info` with `{:workflow-id "..." :cause #<Exception>}` on any non-determinism error.

### Reusable replayer handle

When replaying multiple histories, reuse a single replayer to avoid spinning up a `TestWorkflowEnvironment` per call:

```clojure
(with-open [r (replayer/create)]
  (replayer/replay r (history/from-resource "histories/order-workflow.json"))
  (replayer/replay r (history/from-resource "histories/payment-workflow.json")))
```

`create` accepts an options map:

| Key | Description | Type | Default |
|-----|-------------|------|---------|
| `:dispatch` | Explicit workflow dispatch table | map | auto-dispatch |
| `:data-converter` | Custom data converter | DataConverter | default |

The returned value implements `java.io.Closeable`, so `with-open` handles shutdown automatically.

Full API: [temporal.testing.replayer](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.replayer)

---

## Batch replay in CI

For CI pipelines, load all your history fixtures and replay them in one call:

```clojure
(ns my.replay-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [temporal.testing.history :as history]
            [temporal.testing.replayer :as replayer]))

(deftest replay-all-fixtures
  (let [dir      (io/file "test/resources/histories")
        histories (map history/from-file (.listFiles dir))]
    (with-open [r (replayer/create)]
      (let [{:keys [total failures]} (replayer/replay-all r histories {:fail-fast? false})]
        (is (zero? (count failures))
            (str "Non-determinism in " (count failures) "/" total " workflows: "
                 (map :workflow-id failures)))))))
```

`replay-all` returns a plain Clojure map:

```clojure
{:total    5
 :failures [{:workflow-id "order-123" :error #<NonDeterminismException>}]}
```

The `:fail-fast?` option (default `false`) controls whether replay stops at the first failure or collects all failures.

For a complete runnable example, see the [replay-testing sample](../samples/replay-testing/).

---

## Connection to versioning

Replay testing and workflow versioning work together. Whenever you make a code change that would alter the command sequence of an in-flight workflow, you must guard it with [temporal.workflow/get-version](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#get-version). A replay test will immediately catch you if you forget:

```clojure
(require '[temporal.workflow :as w]
         '[temporal.activity :as a])

;; Safely change from invoke → local-invoke without breaking existing histories
(let [version (w/get-version ::local-activity w/default-version 1)]
  (cond
    (= version w/default-version) @(a/invoke my-activity args)
    (= version 1)                 @(a/local-invoke my-activity args)))
```

See the [Versioning section in the Workflows guide](workflows.md#versioning) for the full pattern and the upstream [Versioning docs](https://docs.temporal.io/develop/java/workflows/versioning).

**Workflow code changes that require versioning:**

- Adding or removing activity invocations
- Reordering commands
- Adding timers or side effects
- Changing workflow arguments used for branching

**Changes that do NOT require versioning:**

- Activity implementation changes (only the command, not its result, is replayed)
- Bug fixes that don't alter the command sequence
- Adding new signals or queries not exercised by existing histories

---

## API reference

### `temporal.testing.history`

| Function | Description |
|----------|-------------|
| [`from-json`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.history#from-json) | Parse a JSON string into a `WorkflowExecutionHistory` |
| [`from-file`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.history#from-file) | Read a history from a file path, `File`, or `Path` |
| [`from-resource`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.history#from-resource) | Read a history from a classpath resource |
| [`fetch`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.history#fetch) | Fetch a history from a live `WorkflowClient` |
| [`->json`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.history#-%3Ejson) | Serialize a history to a JSON string |

### `temporal.testing.replayer`

| Function | Description |
|----------|-------------|
| [`create`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.replayer#create) | Create a reusable replayer handle (`Closeable`) |
| [`replay`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.replayer#replay) | Replay a single history; throws on non-determinism |
| [`replay-all`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.replayer#replay-all) | Batch replay; returns `{:total n :failures [...]}` |
| [`replay-history`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.replayer#replay-history) | One-shot convenience: create → replay → close |
| [`close`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.replayer#close) | Async shutdown |
| [`synchronized-stop`](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.replayer#synchronized-stop) | Blocking shutdown |
