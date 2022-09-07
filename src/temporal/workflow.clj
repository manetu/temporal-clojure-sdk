;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.workflow
  "Methods for defining Temporal workflows"
  (:require [taoensso.timbre :as log]
            [temporal.internal.utils :as u]
            [temporal.internal.workflow :as w]))

(defn get-info
  "
Return info about the current workflow
"
  []
  (w/get-info))

(defmacro defworkflow
  "
Defines a new workflow, similar to defn, expecting a 2-arity parameter list and body.  Should evaluate to something
serializable, which will become available for [[temporal.client.core/get-result]].

Arguments:

- `ctx`: Context passed through from [[temporal.client.worker/start]]
- `params`: A map containing the following
    - `args`: Passed from 'params' to [[temporal.client.core/start]] or [[temporal.client.core/signal-with-start]]
    - `signals`: Signal context for use with signal calls such as [[temporal.signals/<!]] and [[temporal.signals/poll]]

```clojure
(defworkflow my-workflow
    [ctx {{:keys [foo]} :args}]
    ...)

(let [w (create-workflow client my-workflow {:task-queue ::my-task-queue})]
   (start w {:foo \"bar\"}))
```
"
  [name params* & body]
  (let [class-name (u/get-classname name)]
    `(def ~name ^{::w/def ~class-name}
       (fn [ctx# args#]
         (log/trace (str ~class-name ": ") args#)
         (let [f# (fn ~params* (do ~@body))]
           (f# ctx# args#))))))
