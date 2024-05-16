# Child Workflows

## What is a Child Workflow?

A Child Workflow is a Workflow execution spawned from within a Workflow.

Child Workflows orchestrate invocations of Activities just like Workflows do.

Child Workflows should not be used for code organization, however they can be used to partition a Workflow execution's event history into smaller chunks which helps avoid the roughly *~50MB* Workflow event history limit, amongst other use cases.

You should visit the [workflows](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/workflows) page to learn more about Workflows, their constraints, and their executions in general.

For more information about Child Workflows in general visit [Temporal Child Workflows](https://docs.temporal.io/encyclopedia/child-workflows)

## Implementing Child Workflows

In this Clojure SDK programming model, a Temporal Workflow is a function declared with [defworkflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#defworkflow)

And a Child workflow is declared in the exact same way

### Example

```clojure
(require '[temporal.workflow :refer [defworkflow]])

(defworkflow my-workflow
    [{:keys [foo]}]
    ...)
```
## Starting Child Workflow Executions

In this Clojure SDK, Workflows start Child Workflows with [temporal.workflow/invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#invoke)

The options (`ChildWorkflowOptions`) provided to [temporal.workflow/invoke](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.workflow#invoke) are similar to the ones required to create a regular workflow with [temporal.client.core/create-workflow](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#create-workflow)

One big difference however is Child Workflows can provide options to control what happens to themselves with their parents close/fail/complete.

When a Parent Workflow Execution stops, the Temporal Cluster determines what will happen to any running child workflow executions based on the `:parent-close-policy` option.

See [Temporal Parent Close Policy](https://docs.temporal.io/encyclopedia/child-workflows#parent-close-policy) for more information

### Example

```clojure
(require '[temporal.workflow :refer [defworkflow]])
(require '[temporal.activity :refer [defactivity] :as a])

(defactivity child-greeter-activity
  [ctx {:keys [name] :as args}]
  (str "Hi, " name))

(defworkflow child-workflow
  [{:keys [names] :as args}]
  (for [name names]
     @(a/invoke child-greeter-activity {:name name})))

(defworkflow parent-workflow
  [args]
  @(w/invoke child-workflow args {:retry-options {:maximum-attempts 1}
                                  :workflow-task-timeout 10
                                  :workflow-execution-timeout 3600
                                  :workflow-run-timeout 3600}))
```

