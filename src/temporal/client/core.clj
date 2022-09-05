;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.client.core
  "Methods for client interaction with Temporal"
  (:require [taoensso.nippy :as nippy]
            [promesa.core :as p]
            [temporal.internal.workflow :as w]
            [temporal.internal.utils :as u])
  (:import [io.temporal.client WorkflowClient WorkflowStub WorkflowOptions WorkflowOptions$Builder]
           [io.temporal.serviceclient WorkflowServiceStubs]))

(defn create-client
  "
Creates a new client instance suitable for implementing Temporal workers (See [[temporal.client.worker/start]]) or
workflow clients (See [[create-workflow]]).
"
  []
  (let [service (WorkflowServiceStubs/newLocalServiceStubs)]
    (WorkflowClient/newInstance service)))

(def ^:no-doc wf-option-spec
  {:task-queue #(.setTaskQueue ^WorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id #(.setWorkflowId ^WorkflowOptions$Builder %1 %2)})

(defn- wf-options->
  ^WorkflowOptions [params]
  (u/build (WorkflowOptions/newBuilder) wf-option-spec params))

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
  [^WorkflowClient client workflow options]
  (let [wf-name (w/get-annotation workflow)
        stub    (.newUntypedWorkflowStub client wf-name (wf-options-> options))]
    {:client client :stub stub}))

(defn start
  "
Starts 'worklow' with 'params'"
  [{:keys [^WorkflowStub stub] :as workflow} params]
  (.start stub (u/->objarray params)))

(defn signal-with-start
  "
Signals 'workflow' with 'signal-params' on signal 'signal-name', starting it if not already running.  'wf-params' are
used as workflow start arguments if the workflow needs to be started"
  [{:keys [^WorkflowStub stub] :as workflow} signal-name signal-params wf-params]
  (.signalWithStart stub (u/namify signal-name) (u/->objarray signal-params) (u/->objarray wf-params)))

(defn >!
  "
Sends 'params' as a signal 'signal-name' to 'workflow'

```clojure
(>! workflow ::my-signal {:msg \"Hi\"})
```
"
  [{:keys [^WorkflowStub stub] :as workflow} signal-name params]
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
      (p/then nippy/thaw)))

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