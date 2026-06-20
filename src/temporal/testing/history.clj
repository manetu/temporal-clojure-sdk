;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.testing.history
  "Methods for loading, capturing, and serializing Temporal workflow execution histories for use in replay testing."
  (:require [clojure.core.protocols :as p]
            [clojure.java.io :as io])
  (:import [io.temporal.client WorkflowClient]
           [io.temporal.common WorkflowExecutionHistory]))

(extend-protocol p/Datafiable
  WorkflowExecutionHistory
  (datafy [h]
    {:workflow-id (.. h getWorkflowExecution getWorkflowId)
     :run-id      (.. h getWorkflowExecution getRunId)
     :events      (mapv str (.getEvents h))}))

(defn from-json
  "
Parses a JSON string into a `WorkflowExecutionHistory`.

Arguments:

| Value         | Description                                                      | Type   | Default |
| ------------- | ---------------------------------------------------------------- | ------ | ------- |
| `json`        | JSON string containing the serialized workflow execution history  | String |         |
| `workflow-id` | Optional workflow ID override, replaces any ID embedded in JSON  | String | nil     |

```clojure
(from-json \"{...history json...}\")
(from-json \"{...history json...}\" \"my-workflow-id\")
```

See also [[from-file]], [[from-resource]], [[fetch]], [[->json]]
"
  ([json]
   (WorkflowExecutionHistory/fromJson json))
  ([json workflow-id]
   (WorkflowExecutionHistory/fromJson json workflow-id)))

(defn- ->file
  "Coerces a string path, java.io.File, or java.nio.file.Path to java.io.File."
  [f]
  (cond
    (string? f)                      (java.io.File. ^String f)
    (instance? java.nio.file.Path f) (.toFile ^java.nio.file.Path f)
    :else                            f))

(defn from-file
  "
Reads a workflow execution history from a file.

Accepts a string path, `java.io.File`, or `java.nio.file.Path`.

Arguments:

| Value  | Description                                           | Type                                             |
| ------ | ----------------------------------------------------- | ------------------------------------------------ |
| `file` | Path to a JSON file containing the workflow history   | String, `java.io.File`, or `java.nio.file.Path` |

```clojure
(from-file \"test/resources/histories/order.json\")
(from-file (clojure.java.io/file \"test/resources/histories/order.json\"))
```

See also [[from-json]], [[from-resource]], [[fetch]], [[->json]]
"
  [file]
  (-> file ->file slurp from-json))

(defn from-resource
  "
Reads a workflow execution history from a classpath resource.

Throws `ex-info` with `{:resource name}` if the resource is not found on the classpath.

Arguments:

| Value  | Description                                                        | Type   |
| ------ | ------------------------------------------------------------------ | ------ |
| `name` | Classpath resource name for the JSON-serialized workflow history   | String |

```clojure
(from-resource \"histories/order-workflow.json\")
```

See also [[from-json]], [[from-file]], [[fetch]], [[->json]]
"
  [name]
  (let [resource (io/resource name)]
    (when (nil? resource)
      (throw (ex-info (str "Classpath resource not found: " name) {:resource name})))
    (-> resource slurp from-json)))

(defn fetch
  "
Fetches a workflow execution history from a live Temporal server.

Arguments:

| Value         | Description                                                    | Type                                                    |
| ------------- | -------------------------------------------------------------- | ------------------------------------------------------- |
| `client`      | WorkflowClient from [[temporal.client.core/create-client]]     | WorkflowClient                                          |
| `workflow-id` | ID of the workflow whose history to fetch                      | String                                                  |

```clojure
(let [h (fetch client \"workflow-id-123\")]
  (spit \"test/resources/histories/order.json\" (->json h)))
```

See also [[from-json]], [[from-file]], [[from-resource]], [[->json]]
"
  [^WorkflowClient client workflow-id]
  (.fetchHistory client workflow-id))

(defn ->json
  "
Serializes a `WorkflowExecutionHistory` to a JSON string.

Arguments:

| Value           | Description                                             | Type                     | Default |
| --------------- | ------------------------------------------------------- | ------------------------ | ------- |
| `history`       | The workflow execution history to serialize             | WorkflowExecutionHistory |         |
| `pretty-print?` | Whether to format JSON output with indentation          | boolean                  | `true`  |

```clojure
(spit \"order.json\" (->json history))
(spit \"order.json\" (->json history false))
```

See also [[from-json]], [[fetch]]
"
  ([history]
   (->json history true))
  ([^WorkflowExecutionHistory history pretty-print?]
   (.toJson history (boolean pretty-print?))))
