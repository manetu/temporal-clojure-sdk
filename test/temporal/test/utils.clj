;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.utils
  "Utilities common to all tests"
  (:require [taoensso.timbre :as log]
            [temporal.testing.env :as e]
            [temporal.client.core :as c]))

(log/set-level! :trace)

;;-----------------------------------------------------------------------------
;; Data
;;-----------------------------------------------------------------------------
(defonce state (atom {}))

(def task-queue ::default)

;;-----------------------------------------------------------------------------
;; Utilities
;;-----------------------------------------------------------------------------
(defn get-client []
  (get @state :client))

(defn create-workflow [workflow]
  (c/create-workflow (get-client) workflow {:task-queue task-queue}))

;;-----------------------------------------------------------------------------
;; Fixtures
;;-----------------------------------------------------------------------------
(defn create-service []
  (let [{:keys [client] :as env} (e/create task-queue)]
    (swap! state assoc
           :env env
           :client client)))

(defn destroy-service []
  (swap! state
         (fn [{:keys [env] :as s}]
           (e/stop env)
           (dissoc s :env :client))))

(defn wrap-service [test-fn]
  (create-service)
  (test-fn)
  (destroy-service))
