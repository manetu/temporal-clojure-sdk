;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.signals
  "Methods for managing signals from within workflows"
  (:require [taoensso.timbre :as log]
            [temporal.workflow :as w]
            [temporal.internal.utils :as u]
            [temporal.internal.signals :as s])
  (:import [io.temporal.workflow Workflow]))

(defn is-empty?
  "Returns 'true' if 'signal-name' either doesn't exist within signal-chan or exists but has no pending messages"
  [signal-chan signal-name]
  (let [signal-name (u/namify signal-name)
        ch (s/get-ch @signal-chan signal-name)
        r (or (nil? ch) (.isEmpty ch))]
    (log/trace "is-empty?:" @signal-chan signal-name r)
    r))

(defn- rx
  [signal-chan signal-name]
  (let [signal-name (u/namify signal-name)
        ch (s/get-ch @signal-chan signal-name)
        m (.poll ch)]
    (log/trace "rx:" signal-name m)
    m))

(defn poll
  "Non-blocking check of the signal via signal-chan.  Consumes and returns a message if found, otherwise returns 'nil'"
  [signal-chan signal-name]
  (when-not (is-empty? signal-chan signal-name)
    (rx signal-chan signal-name)))

(defn <!
  "Light-weight/parking receive of a single message from signal-chan with an optional timeout"
  ([signal-chan] (<! signal-chan ::default))
  ([signal-chan signal-name] (<! signal-chan signal-name nil))
  ([signal-chan signal-name timeout]
   (log/trace "waiting on:" signal-name "with timeout" timeout)
   (let [pred #(not (is-empty? signal-chan signal-name))]
     (if (some? timeout)
       (do
         (when (w/await timeout pred)
           (rx signal-chan signal-name)))
       (do
         (w/await pred)
         (rx signal-chan signal-name))))))

(defn >!
  "Sends `payload` to `workflow-id` via signal `signal-name`."
  [^String workflow-id signal-name payload]
  (let [signal-name (u/namify signal-name)
        stub (Workflow/newUntypedExternalWorkflowStub workflow-id)]
    (.signal stub signal-name (u/->objarray payload))))

(def register-signal-handler!
  "
Registers a DynamicSignalHandler listener that handles signals sent to the workflow such as with [[>!]].

Use inside a workflow definition with 'f' closing over any desired workflow state (e.g. atom) to mutate
the workflow state.

Arguments:
- `f`: a 2-arity function, expecting 2 arguments.

`f` arguments:
- `signal-name`: string
- `args`: params value or data structure

```clojure
(defworkflow signalled-workflow
  [{:keys [init] :as args}]
  (let [state (atom init)]
    (register-signal-handler! (fn [signal-name args]
                                (when (= signal-name \"mysignal\")
                                  (update state #(conj % args)))))
    ;; workflow implementation
    ))
```"
  s/register-signal-handler!)

(def create-signal-chan
  "Registers the calling workflow to receive signals and returns a 'signal-channel' context for use with functions such as [[<!]] amd [[poll]]"
  s/create-signal-chan)

