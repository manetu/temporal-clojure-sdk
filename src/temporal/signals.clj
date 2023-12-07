;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.signals
  "Methods for managing signals from within workflows"
  (:require [taoensso.timbre :as log]
            [temporal.workflow :as w]
            [temporal.internal.utils :as u]
            [temporal.internal.signals :as s])
  (:import [io.temporal.workflow Workflow]))

(defn is-empty?
  "Returns 'true' if 'signal-name' either doesn't exist or exists but has no pending messages"
  [state signal-name]
  (let [signal-name (u/namify signal-name)
        ch (s/get-ch @state signal-name)
        r (or (nil? ch) (.isEmpty ch))]
    (log/trace "is-empty?:" @state signal-name r)
    r))

(defn- rx
  [state signal-name]
  (let [signal-name (u/namify signal-name)
        ch (s/get-ch @state signal-name)
        m (.poll ch)]
    (log/trace "rx:" signal-name m)
    m))

(defn poll
  "Non-blocking check of the signal.  Consumes and returns a message if found, otherwise returns 'nil'"
  [state signal-name]
  (when-not (is-empty? state signal-name)
    (rx state signal-name)))

(defn <!
  "Light-weight/parking receive of a single message with an optional timeout"
  ([state] (<! state ::default))
  ([state signal-name] (<! state signal-name nil))
  ([state signal-name timeout]
   (log/trace "waiting on:" signal-name "with timeout" timeout)
   (let [pred #(not (is-empty? state signal-name))]
     (if (some? timeout)
       (do
         (when (w/await timeout pred)
           (rx state signal-name)))
       (do
         (w/await pred)
         (rx state signal-name))))))

(defn >!
  "Sends `payload` to `workflow-id` via signal `signal-name`."
  [^String workflow-id signal-name payload]
  (let [signal-name (u/namify signal-name)
        stub (Workflow/newUntypedExternalWorkflowStub workflow-id)]
    (.signal stub signal-name (u/->objarray payload))))