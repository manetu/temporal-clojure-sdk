;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.workflow
  "Methods for defining and implementing Temporal workflows"
  (:require
   [taoensso.nippy :as nippy]
   [taoensso.timbre :as log]
   [temporal.internal.utils :as u]
   [temporal.internal.workflow :as w])
  (:import [io.temporal.workflow DynamicQueryHandler Workflow]
           [java.util.function Supplier]
           [java.time Duration]))

(defn get-info
  "Return info about the current workflow"
  []
  (w/get-info))

(defn- ->supplier
  ^Supplier [f]
  (reify Supplier
    (get [_]
      (f))))

(defn await
  "Efficiently parks the workflow until 'pred' evaluates to true.  Re-evaluates on each state transition"
  ([pred]
   (Workflow/await (->supplier pred)))
  ([^Duration duration pred]
   (Workflow/await duration (->supplier pred))))

(defn sleep
  "Efficiently parks the workflow for 'duration'"
  [^Duration duration]
  (Workflow/sleep duration))


(defn register-query-handler!
  "
Registers a DynamicQueryHandler listener that handles queries sent to the workflow, using [[temporal.client.core/query]].

Use inside a workflow definition with 'f' closing over the workflow state (e.g. atom) and
evaluating to results in function of the workflow state and its 'query-type' and 'query-params' arguments.

Arguments:
- `f`: a 2-arity function, expecting 2 arguments, evaluating to something serializable.

`f` arguments:
- `query-type`: keyword
- `query-args`: params value or data structure

```clojure
(defworkflow stateful-workflow
  [ctx {:keys [signals] {:keys [init] :as args} :args :as params}]
  (let [state (atom init)]
    (register-query-handler! (fn [query-type query-params]
                               (when (= query-type :answer)
                                 (get-in @state [:path :to :answer]))))
    ;; workflow implementation
    ))
```
"
  [f]
  (Workflow/registerListener
    (reify DynamicQueryHandler
      (handle [_ query-type args]
        (let [query-type (keyword query-type)
              args (u/->args args)]
          (log/trace "handling query->" "query-type:" query-type "args:" args)
          (-> (f query-type args)
              (nippy/freeze)))))))


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
  (let [fqn (u/get-fq-classname name)
        sname (str name)]
    `(def ~name ^{::w/def {:name ~sname :fqn ~fqn}}
       (fn [ctx# args#]
         (log/trace (str ~fqn ": ") args#)
         (let [f# (fn ~params* (do ~@body))]
           (f# ctx# args#))))))
