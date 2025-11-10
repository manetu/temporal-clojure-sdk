;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.client.core
  "Methods for client interaction with Temporal"
  (:require [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [promesa.core :as p]
            [temporal.client.options :as copts]
            [temporal.internal.workflow :as w]
            [temporal.internal.utils :as u]
            [temporal.internal.exceptions :as e])
  (:import [java.time Duration]
           [io.temporal.client WorkflowClient WorkflowStub]))

(defn- ^:no-doc create-client*
  [service-stub options]
  (WorkflowClient/newInstance service-stub (copts/workflow-client-options-> options)))

(defn create-client-async
  "
Creates a new client instance suitable for implementing Temporal workers (See [[temporal.client.worker/start]]) or
workflow clients (See [[create-workflow]]).  Will not verify the connection before returning.

Arguments:

- `options`: Options for configuring the `WorkflowClient` (See [[temporal.client.options/workflow-client-options]] and [[temporal.client.options/stub-options]])

"
  ([] (create-client-async {}))
  ([options]
   (create-client* (copts/service-stub-> options) options)))

(defn create-client
  "
Creates a new client instance suitable for implementing Temporal workers (See [[temporal.client.worker/start]]) or
workflow clients (See [[create-workflow]]).  The caller is blocked while the connection is verified for up to the
specified timeout duration, or 5s if not specified.

Arguments:

- `options`: Options for configuring the `WorkflowClient` (See [[temporal.client.options/workflow-client-options]] and [[temporal.client.options/stub-options]])
- `timeout`: Optional connection timeout as a [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) (Default: 5s).

"
  ([] (create-client {}))
  ([options]
   (create-client options (Duration/ofSeconds 5)))
  ([options timeout]
   (create-client* (copts/service-stub-> options timeout) options)))

(defn create-workflow
  "
Create a new workflow-stub instance, suitable for managing and interacting with a workflow through it's lifecycle.

*N.B.: The workflow will remain in an uninitialized and idle state until explicitly started with either ([[start]]) or
([[signal-with-start]]).*

```clojure
(defworkflow my-workflow
  [ctx args]
  ...)

(let [w (create client my-workflow {:task-queue ::my-task-queue})]
  ;; do something with the instance 'w')
```
  "
  ([^WorkflowClient client workflow-id]
   (let [stub    (.newUntypedWorkflowStub client (u/namify workflow-id))]
     (log/trace "create-workflow id:" workflow-id)
     {:client client :stub stub}))
  ([^WorkflowClient client workflow options]
   (let [wf-name (w/get-annotated-name workflow)
         options (w/wf-options-> options)
         stub    (.newUntypedWorkflowStub client wf-name options)]
     (log/trace "create-workflow:" wf-name options)
     {:client client :stub stub})))

(defn start
  "
Starts 'worklow' with 'params'"
  [{:keys [^WorkflowStub stub] :as workflow} params]
  (log/trace "start:" params)
  (.start stub (u/->objarray params)))

(defn signal-with-start
  "
Signals 'workflow' with 'signal-params' on signal 'signal-name', starting it if not already running.  'wf-params' are
used as workflow start arguments if the workflow needs to be started"
  [{:keys [^WorkflowStub stub] :as workflow} signal-name signal-params wf-params]
  (log/trace "signal-with-start->" "signal:" signal-name signal-params "workflow-params:" wf-params)
  (.signalWithStart stub (u/namify signal-name) (u/->objarray signal-params) (u/->objarray wf-params)))

(defn >!
  "
Sends 'params' as a signal 'signal-name' to 'workflow'

```clojure
(>! workflow ::my-signal {:msg \"Hi\"})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} signal-name params]
  (log/trace ">!" signal-name params)
  (.signal stub (u/namify signal-name) (u/->objarray params)))

(defn get-result
  "
Retrieves the final result of 'workflow'.  Returns a promise that when derefed will resolve to the evaluation of the
defworkflow once the workflow concludes.

```clojure
(defworkflow my-workflow
  [ctx args]
  ...)

(let [w (create ...)]
   (start w ...)
   @(get-result w))
```
"
  [{:keys [^WorkflowStub stub] :as workflow}]
  (-> (.getResultAsync stub u/bytes-type)
      (p/then nippy/thaw)
      (p/catch e/slingshot? e/recast-stone)
      (p/catch (fn [e]
                 (log/error e)
                 (throw e)))))

(defn query
  "
Sends query with 'query-type' and 'args' to 'workflow', returns a value.
The query result is computed by a query-handler, registered inside the workflow definition
using [[temporal.workflow/register-query-handler!]].

Arguments:
- `query-type`: keyword (or coerceable into a keyword)
- `args`: serializable query params

```clojure
(query workflow ::my-query {:foo \"bar\"})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} query-type args]
  (-> (.query stub (u/namify query-type) u/bytes-type (u/->objarray args))
      (nippy/thaw)))

(defn cancel
  "
Gracefully cancels 'workflow'

```clojure
(cancel workflow)
```
"
  [{:keys [^WorkflowStub stub] :as workflow}]
  (.cancel stub))

(defn terminate
  "
Forcefully terminates 'workflow'

```clojure
(terminate workflow \"unresponsive\", {})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} reason params]
  (.terminate stub reason (u/->objarray params)))
