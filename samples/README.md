# Temporal Clojure SDK Samples

This directory contains sample Workflow applications that demonstrate the various capabilities of the Temporal Server via the Temporal Clojure SDK.

## Prerequisites

All samples require a running Temporal server. The easiest way is to use the Temporal CLI:

```bash
temporal server start-dev
```

## Samples Directory

### Getting Started

- [**Hello World**](./hello-world): A simple introductory example demonstrating basic workflow and activity usage. Start here if you're new to Temporal.

### Core Patterns

- [**Signals and Queries**](./signals-queries): Demonstrates workflow communication patterns including signal channels, blocking receive, and query handlers for inspecting workflow state.

- [**Updates**](./updates): Shows the new synchronous request-response pattern with updates, including validators, async updates, and update-with-start.

### Advanced Patterns

- [**Saga**](./saga): Implements the Saga pattern for distributed transactions with compensation. Demonstrates multi-step workflows with rollback on failure.

- [**Subscription**](./subscription): A long-running workflow pattern using `continue-as-new` to prevent unbounded history growth. Useful for polling, subscriptions, and periodic tasks.

- [**Batch Processor**](./batch-processor): Parallel processing using child workflows. Demonstrates fan-out patterns with `promise/all` and progress tracking via external workflow signals.

- [**Mutex**](./mutex): Demonstrates distributed locking to coordinate access to shared resources across workflow executions within a namespace.

## Running a Sample

Each sample is a standalone Leiningen project:

```bash
cd samples/<sample-name>
lein run
```

## Sample Structure

Each sample follows a consistent structure:

```
<sample-name>/
├── project.clj          # Dependencies and build configuration
├── README.md            # Documentation and usage instructions
└── src/temporal/sample/<sample_name>/
    ├── core.clj         # Workflows and activities
    └── main.clj         # Entry point
```
