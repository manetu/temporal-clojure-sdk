(ns ^:no-doc temporal.internal.grpc
  (:require [temporal.internal.utils :as u])
  (:import [io.temporal.serviceclient WorkflowServiceStubs WorkflowServiceStubsOptions WorkflowServiceStubsOptions$Builder]))

(def stub-options
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

(defn stub-options->
  ^WorkflowServiceStubsOptions [params]
  (u/build (WorkflowServiceStubsOptions/newBuilder) stub-options params))

(defn service-stub->
  [options timeout]
  (WorkflowServiceStubs/newConnectedServiceStubs (stub-options-> options) timeout))
