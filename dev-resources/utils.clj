(ns utils
  (:require [taoensso.timbre :as log]
            [temporal.activity :as a :refer [defactivity]]
            [temporal.client.core :as c]
            [temporal.client.worker :as worker]
            [temporal.workflow :as w :refer [defworkflow]])
  (:import [java.time Duration]))

(def client (atom nil))
(def current-worker-thread (atom nil))

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

(defworkflow child-workflow
  [{names :names :as args}]
  (log/info "child-workflow:" names)
  (for [name names]
    @(a/invoke greet-activity {:name name})))

(defworkflow parent-workflow
  [args]
  (log/info "parent-workflow:" args)
  @(w/invoke child-workflow args (merge default-workflow-options {:workflow-id "child-workflow"})))

(defn create-temporal-client
  "Creates a new temporal client if the old one does not exist"
  ([] (create-temporal-client nil))
  ([options]
   (when-not @client
     (let [options (merge default-client-options options)]
       (log/info "creating temporal client" options)
       (reset! client (c/create-client options))))))

(defn worker-loop
  ([client] (worker-loop client nil))
  ([client options]
   (let [options (merge default-worker-options options)]
     (log/info "starting temporal worker" options)
     (worker/start client options))))

(defn create-temporal-worker
  "Starts a new instance running on another daemon thread,
   stops the current temporal worker and thread if they exist"
  ([client] (create-temporal-worker client nil))
  ([client options]
   (when (and @current-worker-thread (.isAlive @current-worker-thread))
     (.interrupt @current-worker-thread)
     (reset! current-worker-thread nil))
   (let [thread (Thread. (partial worker-loop client options))]
     (doto thread
       (.setDaemon true)
       (.start))
     (reset! current-worker-thread thread))))

(defn execute-workflow
  ([client workflow arguments] (execute-workflow client workflow arguments nil))
  ([client workflow arguments options]
   (let [options (merge default-workflow-options options)
         workflow (c/create-workflow client workflow options)]
     (log/info "executing workflow" arguments)
     (c/start workflow arguments)
     @(c/get-result workflow))))

(comment
  (do (create-temporal-client)
      (create-temporal-worker @client)
      (execute-workflow @client parent-workflow {:names ["Hanna" "Bob" "Tracy" "Felix"]})))
