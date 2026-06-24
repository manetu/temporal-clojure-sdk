(ns temporal.client.options
  (:require
   [temporal.converter.default :as default-data-converter]
   [temporal.internal.utils :as u])
  (:import
   [io.temporal.authorization AuthorizationTokenSupplier]
   [io.temporal.client WorkflowClientOptions WorkflowClientOptions$Builder WorkflowClientPlugin]
   [io.temporal.client.schedules ScheduleClientOptions ScheduleClientOptions$Builder ScheduleClientPlugin]
   [io.temporal.common.interceptors WorkflowClientInterceptorBase]
   [io.temporal.serviceclient GrpcCompression WorkflowServiceStubs WorkflowServiceStubsOptions WorkflowServiceStubsOptions$Builder WorkflowServiceStubsPlugin]))

(defn assoc-default-data-converter [{:keys [data-converter] :as params}]
  (cond-> params
    (not data-converter)
    (assoc :data-converter (default-data-converter/create))))

(def workflow-client-options
  "
`WorkflowClientOptions` configuration map (See [[temporal.client.core/create-client]])

| Value                     | Description                                                                 | Type         | Default |
| ------------------------- | --------------------------------------------------------------------------- | ------------ | ------- |
| :target                   | Sets the connection host:port                                               | String       | \"127.0.0.1:7233\" |
| :identity                 | Overrides the worker node identity (workers only)                           | String       | |
| :namespace                | Sets the Temporal namespace context for this client                         | String       | |
| :data-converter           | Overrides the data converter used to serialize arguments and results.       | [DataConverter](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/converter/DataConverter.html) | |
| :interceptors             | Collection of interceptors used to intercept workflow client calls.         | [WorkflowClientInterceptor](https://javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/interceptors/WorkflowClientInterceptor.html) | |
| :plugins                  | Collection of plugins to customize workflow client behavior (since Temporal Java SDK 1.33).  | [WorkflowClientPlugin](https://javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/WorkflowClientPlugin.html) | |
"
  {:identity                  #(.setIdentity ^WorkflowClientOptions$Builder %1 %2)
   :namespace                 #(.setNamespace ^WorkflowClientOptions$Builder %1 %2)
   :data-converter            #(.setDataConverter ^WorkflowClientOptions$Builder %1 %2)
   :interceptors              #(.setInterceptors ^WorkflowClientOptions$Builder %1 (into-array WorkflowClientInterceptorBase %2))
   :plugins                   #(.setPlugins ^WorkflowClientOptions$Builder %1 (into-array WorkflowClientPlugin %2))})

(defn ^:no-doc workflow-client-options->
  ^WorkflowClientOptions [params]
  (->> params
       (assoc-default-data-converter)
       (u/build (WorkflowClientOptions/newBuilder (WorkflowClientOptions/getDefaultInstance))
                workflow-client-options)))

(def schedule-client-options
  "
`ScheduleClientOptions` configuration map (See [[temporal.client.schedule/create-client]])

| Value                     | Description                                                                 | Type         | Default |
| ------------------------- | --------------------------------------------------------------------------- | ------------ | ------- |
| :target                   | Sets the connection host:port                                               | String       | \"127.0.0.1:7233\" |
| :identity                 | Overrides the worker node identity (workers only)                           | String       | |
| :namespace                | Sets the Temporal namespace context for this client                         | String       | |
| :data-converter           | Overrides the data converter used to serialize arguments and results.       | [DataConverter](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/converter/DataConverter.html) | |
| :plugins                  | Collection of plugins to customize schedule client behavior (since Temporal Java SDK 1.33).  | [ScheduleClientPlugin](https://javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/schedules/ScheduleClientPlugin.html) | |

"
  {:identity                  #(.setIdentity ^ScheduleClientOptions$Builder %1 %2)
   :namespace                 #(.setNamespace ^ScheduleClientOptions$Builder %1 %2)
   :data-converter            #(.setDataConverter ^ScheduleClientOptions$Builder %1 %2)
   :plugins                   #(.setPlugins ^ScheduleClientOptions$Builder %1 (into-array ScheduleClientPlugin %2))})

(defn ^:no-doc schedule-client-options->
  ^ScheduleClientOptions [params]
  (->> params
       (assoc-default-data-converter)
       (u/build (ScheduleClientOptions/newBuilder (ScheduleClientOptions/getDefaultInstance))
                schedule-client-options)))

(defn ^:no-doc apikey-auth-fn->
  ^AuthorizationTokenSupplier [f]
  (reify AuthorizationTokenSupplier
    (supply [_]
      (f))))

(def stub-options
  "
`WorkflowServiceStubsOptions` configuration map (See [[temporal.client.core/create-client]] or [[temporal.client.schedule/create-client]])

| Value                     | Description                                                                 | Type         | Default |
| ------------------------- | --------------------------------------------------------------------------- | ------------ | ------- |
| :channel                  | Sets gRPC channel to use. Exclusive with target and sslContext              | [ManagedChannel](https://grpc.github.io/grpc-java/javadoc/io/grpc/ManagedChannel.html) | |
| :ssl-context              | Sets gRPC SSL Context to use (See [[temporal.tls/new-ssl-context]])         | [SslContext](https://netty.io/4.0/api/io/netty/handler/ssl/SslContext.html) | |
| :api-key-fn               | Sets [a function to return an API Key](https://docs.temporal.io/develop/java/temporal-client#connect-to-temporal-cloud-api-key) for authentication to Temporal Cloud. **Since Temporal Java SDK 1.33, setting `:api-key-fn` automatically enables TLS** unless `:ssl-context` or `:channel` is also set. Use `:enable-https false` to explicitly opt out. | A 0-arity (fn) that evaluates to an API Key string | |
| :enable-https             | Explicitly enables or disables SSL/TLS/HTTPS for gRPC. When `nil` (not set), TLS is auto-enabled if `:api-key-fn` is provided without `:ssl-context` or `:channel`. | boolean      | nil (auto) |
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
| :grpc-compression         | Sets gRPC transport compression (since Temporal Java SDK 1.36, GZIP is the default). Pass `GrpcCompression/NONE` to disable. | [GrpcCompression](https://javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/serviceclient/GrpcCompression.html) | GZIP |
| :plugins                  | Collection of plugins to customize gRPC stubs behavior (since Temporal Java SDK 1.33).       | [WorkflowServiceStubsPlugin](https://javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/serviceclient/WorkflowServiceStubsPlugin.html) | |

"
  {:channel                  #(.setChannel ^WorkflowServiceStubsOptions$Builder %1 %2)
   :ssl-context              #(.setSslContext ^WorkflowServiceStubsOptions$Builder %1 %2)
   :api-key-fn               #(.addApiKey ^WorkflowServiceStubsOptions$Builder %1 (apikey-auth-fn-> %2))
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
   :metrics-scope            #(.setMetricsScope ^WorkflowServiceStubsOptions$Builder %1 %2)
   :grpc-compression         #(.setGrpcCompression ^WorkflowServiceStubsOptions$Builder %1 %2)
   :plugins                  #(.setPlugins ^WorkflowServiceStubsOptions$Builder %1 (into-array WorkflowServiceStubsPlugin %2))})

(defn stub-options->
  ^WorkflowServiceStubsOptions [params]
  (u/build (WorkflowServiceStubsOptions/newBuilder) stub-options params))

(defn service-stub->
  ([options]
   (WorkflowServiceStubs/newServiceStubs (stub-options-> options)))
  ([options timeout]
   (WorkflowServiceStubs/newConnectedServiceStubs (stub-options-> options) timeout)))
