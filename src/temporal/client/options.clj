(ns temporal.client.options
  (:require [temporal.internal.utils :as u])
  (:import [io.temporal.client WorkflowClientOptions WorkflowClientOptions$Builder]
           [io.temporal.common.interceptors WorkflowClientInterceptorBase]
           [io.temporal.client.schedules ScheduleClientOptions ScheduleClientOptions$Builder]))

(def ^:no-doc client-options
  {:identity                  #(.setIdentity ^WorkflowClientOptions$Builder %1 %2)
   :namespace                 #(.setNamespace ^WorkflowClientOptions$Builder %1 %2)
   :data-converter            #(.setDataConverter ^WorkflowClientOptions$Builder %1 %2)
   :interceptors              #(.setInterceptors ^WorkflowClientOptions$Builder %1 (into-array WorkflowClientInterceptorBase %2))})

(defn ^:no-doc client-options->
  ^WorkflowClientOptions [params]
  (u/build (WorkflowClientOptions/newBuilder (WorkflowClientOptions/getDefaultInstance)) client-options params))

(def ^:no-doc schedule-client-options
  {:identity                  #(.setIdentity ^ScheduleClientOptions$Builder %1 %2)
   :namespace                 #(.setNamespace ^ScheduleClientOptions$Builder %1 %2)
   :data-converter            #(.setDataConverter ^ScheduleClientOptions$Builder %1 %2)})

(defn ^:no-doc schedule-client-options->
  ^ScheduleClientOptions [params]
  (u/build (ScheduleClientOptions/newBuilder (ScheduleClientOptions/getDefaultInstance)) schedule-client-options params))
