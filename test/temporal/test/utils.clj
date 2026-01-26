;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.utils
  "Utilities common to all tests"
  (:require
   [taoensso.timbre :as log]
   [temporal.client.core :as c]
   [temporal.converter.default :as default-data-converter]
   [temporal.converter.json :as json]
   [temporal.testing.env :as e])
  (:import
   [io.temporal.common.converter NullPayloadConverter]
   [java.time Duration]))

(log/set-min-level! :trace)

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
(defn create-service
  ([]
   (create-service {}))

  ([options]
   (let [env     (e/create {:workflow-client-options options})
         client  (e/get-client env)
         worker  (e/start env {:task-queue task-queue})]
     (log/trace "options:" (.getOptions client))
     (swap! state assoc
            :env env
            :worker worker
            :client client))))

(defn destroy-service []
  (swap! state
         (fn [{:keys [env] :as s}]
           (e/synchronized-stop env)
           (dissoc s :env :worker :client))))

(def data-converter
  (default-data-converter/create
   [(NullPayloadConverter.)
    (json/create)]))

(defn wrap-service [test-fn]
  (create-service #_{:data-converter data-converter})
  (test-fn)
  (destroy-service))
