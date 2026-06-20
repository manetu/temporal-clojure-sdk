# Replay Testing Sample

Demonstrates how to use `temporal.testing.history` and `temporal.testing.replayer` to harden your workflows against non-determinism bugs before they reach production workers.

## What This Sample Shows

- How to **capture** a workflow's event history from an in-memory test environment
- How to **save** a history to a JSON fixture file and commit it to version control
- How to **replay** a pre-captured fixture in CI without a running Temporal server
- How to **detect non-determinism** — an activity-order change is caught before deployment

## Why Replay Testing Matters

Temporal workflows must be **deterministic**: replaying a workflow's persisted event history against its code must produce the identical command sequence. Any code change that:

- reorders activities or commands
- adds or removes workflow steps without versioning
- branches on non-deterministic inputs (wall-clock time, random values, external state)

…breaks replay — silently, until a worker tries to resume an in-flight workflow in production.

The standard mitigation is: **capture representative production histories → replay them in CI → fail the build on any mismatch**.

## Workflow Under Test

`order-workflow` calls two activities in sequence:

1. `validate-order` — checks the order is eligible for processing
2. `charge-payment` — charges the customer

Swapping these activities breaks replay. The sample's `workflow-version` atom simulates this code change so the test can demonstrate the detection without an actual redeploy.

## Running the Tests

First install the SDK into your local Maven cache from the repo root:

```bash
# from the repo root
lein install
```

Then run the tests:

```bash
cd samples/replay-testing
lein test
```

All tests use the in-memory `TestWorkflowEnvironment` — **no running Temporal server required**.

## Understanding the Tests

| Test | What It Shows |
|------|---------------|
| `replay-live-history-test` | Basic: run the workflow, fetch history, replay it |
| `replay-from-fixture-test` | Capture history to a JSON file, reload and replay from file |
| `replay-from-precaptured-fixture-test` | Load and replay the committed `order.json` fixture — the pure CI pattern |
| `replay-detects-nondeterminism-test` | Confirm that reversing activity order throws during replay |
| `replay-all-test` | Batch replay of multiple histories via `replay-all` |

## The Capture-Then-Commit Workflow

```
1. Run your workflow against a staging or test environment.
2. Capture the history:
       (history/->json (history/fetch client workflow-id))
3. Commit the JSON file to version control.
4. In CI, load and replay:
       (replayer/replay-history (history/from-resource "histories/order.json"))
```

## Links

- [Replay Testing Guide](../../doc/replay_testing.md)
- [temporal.testing.history API](https://cljdoc.org/d/io.github.manetu/temporal-sdk/latest/api/temporal.testing.history)
- [temporal.testing.replayer API](https://cljdoc.org/d/io.github.manetu/temporal-sdk/latest/api/temporal.testing.replayer)
- [Temporal Testing Suite — Java SDK](https://docs.temporal.io/develop/java/testing-suite)
