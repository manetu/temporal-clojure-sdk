;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.utils
  "Utilities common to all tests"
  (:require [taoensso.timbre :as log]
            [temporal.testing.env :as e]
            [temporal.client.core :as c])
  (:import [java.time Duration]))

(log/set-level! :trace)

;;-----------------------------------------------------------------------------
;; Data
;;-----------------------------------------------------------------------------
(defonce state (atom {}))

(def task-queue ::default)

;;-----------------------------------------------------------------------------
;; Utilities
;;-----------------------------------------------------------------------------
(defn get-worker []
  (get @state :worker))

(defn get-client []
  (get @state :client))

(defn create-workflow [workflow]
  (c/create-workflow (get-client) workflow {:task-queue task-queue :workflow-execution-timeout (Duration/ofSeconds 10) :retry-options {:maximum-attempts 1}}))

;;-----------------------------------------------------------------------------
;; Fixtures
;;-----------------------------------------------------------------------------
(defn create-service []
  (let [env    (e/create)
        client (e/get-client env)
        worker (e/start env {:task-queue task-queue})]
    (swap! state assoc
           :env env
           :worker worker
           :client client)))

(defn destroy-service []
  (swap! state
         (fn [{:keys [env] :as s}]
           (e/synchronized-stop env)
           (dissoc s :env :worker :client))))

(defn wrap-service [test-fn]
  (create-service)
  (test-fn)
  (destroy-service))
