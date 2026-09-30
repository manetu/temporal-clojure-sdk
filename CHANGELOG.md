# Changelog

## Unreleased

### Temporal Java SDK 1.38.0 → 1.40.0

Bumped `io.temporal/temporal-shaded` and `temporal-opentracing` to 1.40.0.

**Breaking change handled**

- `StartActivityOptions.Builder.setStaticSummary` was renamed `setSummary` upstream (1.39).  The `:static-summary` option key on [[temporal.client.activity]] is unchanged.

**Newly generally available**

- Standalone Activities (`temporal.client.activity`) are GA as of 1.39; upstream removed `@Experimental`.  Documentation updated accordingly.

**Behavior changes verified (no action required)**

- SDK flags `CANCEL_AWAIT_TIMER_ON_CONDITION` and `VERSION_WAIT_FOR_MARKER` are on by default (1.40).  Histories recorded under 1.38 still replay; a regression test pins this.
- `ActivitySerializationContext.getActivityType()/getActivityTaskQueue()` are now `@Nullable` (1.40), `ActivityStub` gained `ActivityInvocationOptions` overloads, and `ActivityInput`/`LocalActivityInput` constructors are deprecated (1.40).  This wrapper does not use them.
- Nexus input deserialization failures and `PayloadValidationError` are now non-retryable `BAD_REQUEST` (1.39); local-activity unbounded timeout failure chain fixed (1.39); the test server now propagates headers on signals (1.40).

**Deliberately not wrapped (tracked only)**

- External Storage (large-payload offload, Public Preview), `ActivityInvocationOptions`, `NexusSerializationContext`, and other `@Experimental` APIs.

### Temporal Java SDK 1.36.0 → 1.38.0

- `ActivityCancellationToken` replaced upstream by `io.temporal.common.CancellationToken` (transparent to callers).
- New options: `:disable-eager-execution`, `:max-eager-activity-reservations-per-workflow-task`, `:static-summary`/`:static-details` on workflows and child workflows, `:summary` on activity options.
- New functions: `temporal.workflow/new-timer`, `set-current-details`, `get-current-details`; `temporal.client.activity/get-handle`.

### Temporal Java SDK 1.32.1 → 1.36.0

- TLS auto-enabled when an API key is provided (1.33); search attributes carried over on continue-as-new (1.33); GZIP gRPC compression on by default (1.36, opt out via `:grpc-compression`).
- New: Plugin API (`:plugins`), `get-cancellation-token`, additional continue-as-new versioning intents, and shutdown-related worker options.
