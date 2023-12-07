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

(defn create
  "Registers the calling workflow to receive signals and returns a context variable to be passed back later"
  []
  (let [state (atom {})]
    (Workflow/registerListener
     (reify
       DynamicSignalHandler
       (handle [_ signal-name args]
         (-handle state signal-name (u/->args args)))))
    state))
