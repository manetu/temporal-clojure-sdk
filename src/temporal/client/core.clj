;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.client.core
  "Methods for client interaction with Temporal"
  (:require [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [promesa.core :as p]
            [temporal.common :as common]
            [temporal.internal.workflow :as w]
            [temporal.internal.utils :as u]
            [temporal.internal.exceptions :as e])
  (:import [java.time Duration]
           [io.temporal.client WorkflowClient WorkflowClientOptions WorkflowClientOptions$Builder WorkflowStub
            WorkflowOptions WorkflowOptions$Builder]
           [io.temporal.serviceclient WorkflowServiceStubs WorkflowServiceStubsOptions WorkflowServiceStubsOptions$Builder]
           [io.temporal.common.interceptors WorkflowClientInterceptorBase]
           [io.temporal.api.enums.v1 WorkflowIdReusePolicy]))

(def ^:no-doc stub-options
  {:channel                  #(.setChannel ^WorkflowServiceStubsOptions$Builder %1 %2)
   :ssl-context              #(.setSslContext ^WorkflowServiceStubsOptions$Builder %1 %2)
   :enable-https             #(.setEnableHttps ^WorkflowServiceStubsOptions$Builder %1 %2)
   :target                   #(.setTarget ^WorkflowServiceStubsOptions$Builder %1 %2)
   :rpc-timeout              #(.setRpcTimeout ^WorkflowServiceStubsOptions$Builder %1 %2)
   :rpc-long-poll-timeout    #(.setRpcLongPollTimeout ^WorkflowServiceStubsOptions$Builder %1 %2)
   :rpc-query-timeout        #(.setRpcQueryTimeout ^WorkflowServiceStubsOptions$Builder %1 %2)
   :backoff-reset-freq       #(.setConnectionBackoffResetFrequency ^WorkflowServiceStubsOptions$Builder %1 %2)
   :grpc-reconnect-freq      #(.setGrpcReconnectFrequency ^WorkflowServiceStubsOptions$Builder %1 %2)
   :headers                  #(.setHeaders ^WorkflowServiceStubsOptions$Builder %1 %2)
   :enable-keepalive         #(.setEnableKeepAlive ^WorkflowServiceStubsOptions$Builder %1 %2)
   :keepalive-time           #(.setKeepAliveTime ^WorkflowServiceStubsOptions$Builder %1 %2)
   :keepalive-timeout        #(.setKeepAliveTimeout ^WorkflowServiceStubsOptions$Builder %1 %2)
   :keepalive-without-stream #(.setKeepAlivePermitWithoutStream ^WorkflowServiceStubsOptions$Builder %1 %2)
   :metrics-scope            #(.setMetricsScope ^WorkflowServiceStubsOptions$Builder %1 %2)})

(defn ^:no-doc stub-options->
  ^WorkflowServiceStubsOptions [params]
  (u/build (WorkflowServiceStubsOptions/newBuilder) stub-options params))

(def ^:no-doc client-options
  {:identity                  #(.setIdentity ^WorkflowClientOptions$Builder %1 %2)
   :namespace                 #(.setNamespace ^WorkflowClientOptions$Builder %1 %2)
   :data-converter            #(.setDataConverter ^WorkflowClientOptions$Builder %1 %2)
   :interceptors              #(.setInterceptors ^WorkflowClientOptions$Builder %1 (into-array WorkflowClientInterceptorBase %2))})

(defn ^:no-doc client-options->
  ^WorkflowClientOptions [params]
  (u/build (WorkflowClientOptions/newBuilder (WorkflowClientOptions/getDefaultInstance)) client-options params))

(defn create-client
  "
Creates a new client instance suitable for implementing Temporal workers (See [[temporal.client.worker/start]]) or
workflow clients (See [[create-workflow]]).

Arguments:

- `options`: Client configuration option map (See below)
- `timeout`: Connection timeout as a [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) (default: 5s)

#### options map


| Value                     | Description                                                                 | Type         | Default |
| ------------------------- | --------------------------------------------------------------------------- | ------------ | ------- |
| :target                   | Sets the connection host:port                                               | String       | \"127.0.0.1:7233\" |
| :identity                 | Overrides the worker node identity (workers only)                           | String       | |
| :namespace                | Sets the Temporal namespace context for this client                         | String       | |
| :data-converter           | Overrides the data converter used to serialize arguments and results.       | [DataConverter](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/converter/DataConverter.html) | |
| :interceptors             | Collection of interceptors used to intercept workflow client calls.         | [WorkflowClientInterceptor](https://javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/interceptors/WorkflowClientInterceptor.html) | |
| :channel                  | Sets gRPC channel to use. Exclusive with target and sslContext              | [ManagedChannel](https://grpc.github.io/grpc-java/javadoc/io/grpc/ManagedChannel.html) | |
| :ssl-context              | Sets gRPC SSL Context to use (See [[temporal.tls/new-ssl-context]])         | [SslContext](https://netty.io/4.0/api/io/netty/handler/ssl/SslContext.html) | |
| :enable-https             | Sets option to enable SSL/TLS/HTTPS for gRPC                                | boolean      | false |
| :rpc-timeout              | Sets the rpc timeout value for non query and non long poll calls            | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 10s |
| :rpc-long-poll-timeout    | Sets the rpc timeout value                                                  | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 60s |
| :rpc-query-timeout        | Sets the rpc timeout for queries                                            | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 10s |
| :backoff-reset-freq       | Sets frequency at which gRPC connection backoff should be reset practically | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 10s |
| :grpc-reconnect-freq      | Sets frequency at which gRPC channel will be moved into an idle state       | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | 60s |
| :headers                  | Set the headers                                                             | [Metadata](https://grpc.github.io/grpc-java/javadoc/io/grpc/Metadata.html) | |
| :enable-keepalive         | Set keep alive ping from client to the server                               | boolean       | false |
| :keepalive-time           | Set the keep alive time                                                     | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :keepalive-timeout        | Set the keep alive timeout                                                  | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :keepalive-without-stream | Set if client sends keepalive pings even with no active RPCs                | boolean       | false |
| :metrics-scope            | The scope to be used for metrics reporting                                  | [Scope](https://github.com/uber-java/tally/blob/master/core/src/main/java/com/uber/m3/tally/Scope.java) | |

"
  ([] (create-client {}))
  ([options]
   (create-client options (Duration/ofSeconds 5)))
  ([options timeout]
   (let [service (WorkflowServiceStubs/newConnectedServiceStubs (stub-options-> options) timeout)]
     (WorkflowClient/newInstance service (client-options-> options)))))

(def workflow-id-reuse-options
  "
| Value                        | Description                                                                  |
| ---------------------------- | --------------------------------------------------------------------------- |
| :allow-duplicate             | Allow starting a workflow execution using the same workflow id.             |
| :allow-duplicate-failed-only | Allow starting a workflow execution using the same workflow id, only when the last execution's final state is one of [terminated, cancelled, timed out, failed] |
| :reject-duplicate            | Do not permit re-use of the workflow id for this workflow.                  |
| :terminate-if-running        | If a workflow is running using the same workflow ID, terminate it and start a new one. If no running workflow, then the behavior is the same as ALLOW_DUPLICATE|
"
  {:allow-duplicate             WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE
   :allow-duplicate-failed-only WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY
   :reject-duplicate            WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE
   :terminate-if-running        WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING})

(defn ^:no-doc workflow-id-reuse-policy->
  ^WorkflowIdReusePolicy [policy]
  (or (get workflow-id-reuse-options policy)
      (throw (IllegalArgumentException. (str "Unknown workflow-id-reuse-policy: " policy " Must be one of " (keys workflow-id-reuse-options))))))

(def ^:no-doc wf-option-spec
  {:task-queue                 #(.setTaskQueue ^WorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id                #(.setWorkflowId ^WorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id-reuse-policy   #(.setWorkflowIdReusePolicy ^WorkflowOptions$Builder %1 (workflow-id-reuse-policy-> %2))
   :workflow-execution-timeout #(.setWorkflowExecutionTimeout ^WorkflowOptions$Builder %1 %2)
   :workflow-run-timeout       #(.setWorkflowRunTimeout ^WorkflowOptions$Builder %1 %2)
   :workflow-task-timeout      #(.setWorkflowTaskTimeout ^WorkflowOptions$Builder %1 %2)
   :retry-options              #(.setRetryOptions %1 (common/retry-options-> %2))
   :cron-schedule              #(.setCronSchedule ^WorkflowOptions$Builder %1 %2)
   :memo                       #(.setMemo ^WorkflowOptions$Builder %1 %2)
   :search-attributes          #(.setSearchAttributes ^WorkflowOptions$Builder %1 %2)})

(defn ^:no-doc wf-options->
  ^WorkflowOptions [params]
  (u/build (WorkflowOptions/newBuilder (WorkflowOptions/getDefaultInstance)) wf-option-spec params))

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
         options (wf-options-> options)
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