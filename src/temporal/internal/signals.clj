;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.signals
  (:require [taoensso.timbre :as log]
            [temporal.internal.utils :as u])
  (:import [java.util.concurrent ConcurrentLinkedQueue]
           [io.temporal.workflow Workflow DynamicSignalHandler]))

(defn get-ch
  ^ConcurrentLinkedQueue [s signal-name]
  (get s signal-name))

(defn- -handle
  [state signal-name payload]
  (log/trace "dispatching signal:" signal-name payload)
  (swap! state
         (fn [s]
           (let [ch (or (get-ch s signal-name)
                        (ConcurrentLinkedQueue.))]
             (log/trace "saving signal:" signal-name payload)
             (.add ch payload)
             (assoc s signal-name ch)))))

(defn register-signal-handler!
  [f]
  (Workflow/registerListener
   (reify
     DynamicSignalHandler
     (handle [_ signal-name args]
       (f signal-name (u/->args args))))))

(defn create-signal-chan
  []
  (let [state (atom {})]
    (register-signal-handler! (partial -handle state))
    state))
