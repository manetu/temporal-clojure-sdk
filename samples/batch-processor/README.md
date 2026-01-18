# Batch Processor Sample

Demonstrates child workflows and parallel processing patterns.

## What This Sample Shows

- Using child workflows for parallel fan-out processing
- Aggregating results from multiple child workflows
- Using `temporal.promise/all` for concurrent execution
- Using `external-workflow` to signal between workflows
- Progress tracking via updates

## Two Patterns Demonstrated

### Pattern 1: Promise/All

Simple parallel execution where you wait for all children to complete:

```
Parent Workflow
    │
    ├──→ Child 1 ──┐
    ├──→ Child 2 ──┼──→ promise/all ──→ Aggregate
    ├──→ Child 3 ──┤
    └──→ Child 4 ──┘
```

### Pattern 2: Signal-Based Progress

Children signal completion to parent, enabling progress tracking:

```
Parent Workflow (waiting for signals)
    │                    ↑
    ├──→ Child 1 ────────┤ (signal ::item-completed)
    ├──→ Child 2 ────────┤
    ├──→ Child 3 ────────┤
    └──→ Child 4 ────────┘
```

## Prerequisites

A running Temporal server:

```bash
temporal server start-dev
```

## Running the Sample

```bash
cd samples/batch-processor
lein run
```

## Expected Output

```
==============================================
Pattern 1: Parallel Processing with promise/all
==============================================

Starting child workflows with promise/all...
Item processor workflow started for: ITEM-001
Item processor workflow started for: ITEM-002
...
All child workflows completed
Aggregating 5 results for batch: BATCH-001
Batch result: {:batch-id "BATCH-001", :total-items 5, ...}

==============================================
Pattern 2: Progress Tracking with Signals
==============================================

Starting child workflows with signal notification...
Waiting for completion signals...
Received completion for item: ITEM-003
Received completion for item: ITEM-001
...
Batch result: {:batch-id "BATCH-002", :total-items 5, ...}
```

## Key Concepts

### Child Workflows

Child workflows are started with `w/invoke`:

```clojure
(w/invoke item-processor-workflow
          {:item-id id :data data}
          {:task-queue task-queue})
```

Benefits over activities:
- Independent retry policies
- Separate execution history
- Can be queried individually
- Can run for long periods

### Parallel Execution with promise/all

Execute multiple children and wait for all:

```clojure
(let [promises (mapv #(w/invoke child-workflow %) items)
      results @(tp/all promises)]
  ;; All children completed
  results)
```

### External Workflow Signals

Children can notify parent using external-workflow:

```clojure
;; In child workflow
(let [parent (w/external-workflow parent-workflow-id)]
  (w/signal-external parent ::completed result))

;; In parent workflow
(when-let [result (s/<! signals ::completed)]
  (process result))
```

### Progress Tracking

Register an update handler to expose progress:

```clojure
(w/register-update-handler!
  (fn [update-type _]
    (case update-type
      :get-progress {:completed (count @results)
                     :total total-items})))

;; Client queries progress
(c/update workflow :get-progress {})
```

## Use Cases

- **Data processing**: Process files, records, or events in parallel
- **Order fulfillment**: Process multiple order items concurrently
- **Report generation**: Gather data from multiple sources
- **Batch jobs**: ETL, data migration, bulk operations
