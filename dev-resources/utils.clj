(ns utils
  (:require [taoensso.timbre :as log]
            [temporal.activity :as a :refer [defactivity]]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.workflow :as w :refer [defworkflow]])
  (:import [java.time Duration]))

(def default-client-options {:target "localhost:7233"
                             :namespace "default"
                             :enable-https false})

(def default-worker-options {:task-queue "default"})

(def default-workflow-options {:task-queue "default"
                               :workflow-execution-timeout (Duration/ofSeconds 30)
                               :retry-options {:maximum-attempts 1}
                               :workflow-id "test-workflow"})

(defactivity greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow user-child-workflow
  [{names :names :as args}]
  (log/info "child-workflow:" names)
  (for [name names]
    @(a/invoke greet-activity {:name name})))

(defworkflow user-parent-workflow
  [args]
  (log/info "parent-workflow:" args)
  @(w/invoke user-child-workflow args (merge default-workflow-options {:workflow-id "child-workflow"})))

(defn create-temporal-client
  ([] (create-temporal-client nil))

  ([options]
   (let [options (merge default-client-options options)]
     (log/info "creating temporal client" options)
     (c/create-client options))))

(defn create-temporal-worker
  ([client] (create-temporal-worker client nil))

  ([client options]
   (let [options (merge default-worker-options options)]
     (log/info "starting temporal worker" options)
     (worker/start client options))))

(defn execute-workflow
  ([client workflow arguments] (execute-workflow client workflow arguments nil))
  ([client workflow arguments options]
   (let [options (merge default-workflow-options options)
         workflow (c/create-workflow client workflow options)]
     (log/info "executing workflow" arguments)
     (c/start workflow arguments)
     @(c/get-result workflow))))

(comment
  (let [client (create-temporal-client)
        worker (create-temporal-worker client)]
    (try
      (execute-workflow client user-parent-workflow {:names ["Hanna" "Bob" "Tracy" "Felix"]})
      (finally
        (worker/stop worker)))))
