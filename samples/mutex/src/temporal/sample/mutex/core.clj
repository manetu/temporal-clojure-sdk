(ns temporal.sample.mutex.core
  (:require [taoensso.timbre :as log]
            [promesa.core :as p]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker.client]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.signals :refer [poll <! >!]]
            [temporal.side-effect :refer [gen-uuid]]
            [environ.core :refer [env]])
  (:import [java.time Duration]
           [io.temporal.workflow Workflow]))

(defworkflow mutex-workflow
  [ctx {:keys [signals]}]
  (log/info "starting mutex workflow:" (w/get-info))
  (loop []
    (when-let [sender-id (poll signals ::acquire)]
      (let [release-ch (gen-uuid)]
        (log/info "granting:" sender-id "release-ch:" release-ch)
        (>! sender-id ::acquired release-ch)
        (log/info "waiting for release")
        (<! signals release-ch)
        (log/info "lock released")
        (recur)))))

(defactivity start-mutex-activity
  [{:keys [client] :as ctx} {:keys [sender-id mutex-id]}]
  (log/info "start-mutex-activity:" mutex-id)
  (let [wf (c/create-workflow client mutex-workflow {:task-queue ::queue :workflow-id mutex-id :retry-options {:maximum-attempts 1} :workflow-execution-timeout (Duration/ofMinutes 1)})]
    (c/signal-with-start wf ::acquire sender-id nil)
    :ok))

(defn lock
  [signals resource-id]
  (log/info "lock:" resource-id)
  (let [{:keys [workflow-id]} (w/get-info)
        mutex-id (str "mutex-" resource-id)]
    @(a/local-invoke start-mutex-activity {:sender-id workflow-id :mutex-id mutex-id})
    (log/info "waiting for acquisition")
    (let [release-ch (<! signals ::acquired)]
      (log/info "lock acquired:" release-ch)
      (fn []
        (log/info "releasing lock")
        (>! mutex-id release-ch {})))))

(defworkflow sample-workflow
  [ctx {:keys [signals] {:keys [resource-id]} :args}]
  (log/info "starting sample-workflow with resource-id:" resource-id)
  (let [release-fn (lock signals resource-id)]
    (log/info "enter critical section")
    (comment Workflow/sleep (Duration/ofMillis 100))
    (log/info "leaving critical section")
    (release-fn)))

(defn invoke-sample
  [client resource-id]
  (let [wf (c/create-workflow client sample-workflow {:task-queue ::queue})]
    (c/start wf {:resource-id resource-id})
    (c/get-result wf)))

(defn init []
  (let [client (c/create-client {:target (env :temporal-target "127.0.0.1:7233") :namespace (env :temporal-namespace "default")})
        worker (worker.client/start client {:task-queue ::queue :ctx {:client client}})]
    @(p/all (map (fn [_] (invoke-sample client "foo")) (range 10)))
    (worker.client/stop worker)))