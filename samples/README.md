# Temporal Clojure SDK samples

This directory contains several sample Workflow applications that demonstrate the various capabilities of the Temporal Server via the Temporal Clojure SDK.

## Samples directory

- [**Mutex Workflow**](./mutex/README.md): Demonstrates the ability to lock/unlock a particular resource within a particular Temporal Namespace. In this examples the other Workflow Executions within the same Namespace wait until a locked resource is unlocked. This shows how to avoid race conditions or parallel mutually exclusive operations on the same resource.
