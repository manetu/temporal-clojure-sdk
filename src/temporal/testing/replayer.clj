;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.testing.replayer
  "Methods for replay-testing Temporal workflows against their persisted event histories."
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [temporal.client.worker :as worker]
            [temporal.testing.env :as env])
  (:import [io.temporal.testing WorkflowReplayer TestWorkflowEnvironment ReplayResults ReplayResults$ReplayError]
           [io.temporal.common WorkflowExecutionHistory]
           [io.temporal.worker Worker]))

(extend-protocol p/Datafiable
  ReplayResults
  (datafy [r]
    {:failures (mapv d/datafy (.allErrors r))})

  ReplayResults$ReplayError
  (datafy [e]
    {:workflow-id (.-workflowId e)
     :error       (.-exception e)}))

(def replayer-options
  "Options map accepted by [[create]].

| Key                | Description                                          | Type                           | Default |
| ------------------ | ---------------------------------------------------- | ------------------------------ | ------- |
| `:dispatch`        | Explicit workflow/activity dispatch table            | map with `:workflows`/`:activities` keys | auto-dispatch |
| `:data-converter`  | Custom data converter for decoding history payloads  | [DataConverter](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/converter/DataConverter.html) | default |
"
  {})

(defrecord Replayer [env worker]
  java.io.Closeable
  (close [this]
    (.close ^TestWorkflowEnvironment (:env this))))

(defn create
  "
Creates a reusable replayer handle backed by a `TestWorkflowEnvironment` with dynamic dispatch registered.

The returned value implements `java.io.Closeable` and may be used with `with-open`.

Arguments:

- `options`: Configuration map (See [[replayer-options]])

```clojure
(with-open [r (create {:data-converter my-converter})]
  (replay r (history/from-resource \"histories/order.json\")))
```

See also [[replay]], [[replay-all]], [[replay-history]]
"
  (^Replayer []
   (create {}))

  (^Replayer [{:keys [data-converter dispatch]}]
   (let [task-queue  (str "replay-" (java.util.UUID/randomUUID))
         env-options (cond-> {}
                       data-converter (assoc :workflow-client-options {:data-converter data-converter}))
         env         (env/create env-options)
         worker-obj  (.newWorker env task-queue (worker/worker-options-> {}))
         _           (worker/init worker-obj {:dispatch dispatch})
         _           (.start env)]
     (->Replayer env worker-obj))))

(defn replay
  "
Replays a single workflow execution history against the registered workflows.

Throws `ex-info` with `{:workflow-id ... :cause e}` if the history does not replay cleanly
(i.e. a non-determinism error is detected).

Arguments:

| Value       | Description                            | Type                     |
| ----------- | -------------------------------------- | ------------------------ |
| `replayer`  | Handle returned from [[create]]        | Replayer                 |
| `history`   | Workflow execution history to replay   | WorkflowExecutionHistory |

```clojure
(with-open [r (create)]
  (replay r (history/from-resource \"histories/order.json\")))
```

See also [[replay-all]], [[replay-history]]
"
  [replayer ^WorkflowExecutionHistory history]
  (try
    (WorkflowReplayer/replayWorkflowExecution history (:worker replayer))
    (catch Exception e
      (throw (ex-info "Workflow replay failed: non-determinism detected"
                      {:workflow-id (some-> history .getWorkflowExecution .getWorkflowId)
                       :cause       e})))))

(defn replay-all
  "
Replays multiple workflow execution histories against the registered workflows.

Returns a map `{:total n :failures [{:workflow-id \"...\" :error #<Exception>} ...]}`.
`:total` is the count of all input histories; `:failures` contains one entry per non-determinism error.

Arguments:

| Value       | Description                                                    | Type                           |
| ----------- | -------------------------------------------------------------- | ------------------------------ |
| `replayer`  | Handle returned from [[create]]                                | Replayer                       |
| `histories` | Sequence of workflow histories to replay                       | Iterable of WorkflowExecutionHistory |
| `opts`      | Options map `{:fail-fast? bool}` — stop on first failure when `true`. When `:fail-fast? true` and a failure occurs, throws `ex-info` with `{:cause e}` rather than returning a result map. | map |

```clojure
(with-open [r (create)]
  (let [{:keys [total failures]} (replay-all r histories {:fail-fast? false})]
    (when (seq failures)
      (throw (ex-info \"Non-determinism detected\" {:failures failures})))))
```

See also [[replay]], [[replay-history]]
"
  ([replayer histories]
   (replay-all replayer histories {}))

  ([replayer histories {:keys [fail-fast?] :or {fail-fast? false}}]
   (let [^Iterable hs (vec histories)
         ^Worker worker (:worker replayer)]
     (try
       (let [results (WorkflowReplayer/replayWorkflowExecutions hs (boolean fail-fast?) worker)]
         (assoc (d/datafy results) :total (count hs)))
       (catch Exception e
         (throw (ex-info "Workflow replay failed: non-determinism detected"
                         {:cause e})))))))

(defn close
  "
Shuts down the replayer environment asynchronously.  Does not wait for shutdown to complete.
For coordinated shutdown, see [[synchronized-stop]].

```clojure
(close replayer)
```
"
  [replayer]
  (.shutdown ^TestWorkflowEnvironment (:env replayer)))

(defn synchronized-stop
  "
Shuts down the replayer environment and blocks until it has fully stopped.
For async shutdown, see [[close]].

```clojure
(synchronized-stop replayer)
```
"
  [replayer]
  (.close ^TestWorkflowEnvironment (:env replayer)))

(defn replay-history
  "
One-shot convenience function: creates a replayer, replays a single history, then closes.

Throws `ex-info` with `{:workflow-id ... :cause e}` on non-determinism.

Arguments:

| Value     | Description                                | Type                     | Default |
| --------- | ------------------------------------------ | ------------------------ | ------- |
| `history` | Workflow execution history to replay       | WorkflowExecutionHistory |         |
| `opts`    | Options map forwarded to [[create]]        | map                      | `{}`    |

```clojure
(replay-history (history/from-resource \"histories/order-workflow.json\"))
(replay-history h {:data-converter my-converter})
```

See also [[create]], [[replay]]
"
  ([history]
   (replay-history history {}))

  ([history opts]
   (with-open [r (create opts)]
     (replay r history))))
