;; Copyright © Manetu, Inc.  All rights reserved

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

(def default-version (int Workflow/DEFAULT_VERSION))

(defn get-version
  "Used to safely perform backwards incompatible changes to workflow definitions"
  [change-id min max]
  (Workflow/getVersion (u/namify change-id) min max))

(defn register-query-handler!
  "
Registers a DynamicQueryHandler listener that handles queries sent to the workflow, using [[temporal.client.core/query]].

Use inside a workflow definition with 'f' closing over the workflow state (e.g. atom) and
evaluating to results in function of the workflow state and its 'query-type' and 'args' arguments.

Arguments:
- `f`: a 2-arity function, expecting 2 arguments, evaluating to something serializable.

`f` arguments:
- `query-type`: keyword
- `args`: params value or data structure

```clojure
(defworkflow stateful-workflow
  [{:keys [init] :as args}]
  (let [state (atom init)]
    (register-query-handler! (fn [query-type args]
                               (when (= query-type :my-query)
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
Defines a new workflow, similar to defn, expecting a 1-arity parameter list and body.  Should evaluate to something
serializable, which will become available for [[temporal.client.core/get-result]].

Arguments:

- `args`: Passed from 'params' to [[temporal.client.core/start]] or [[temporal.client.core/signal-with-start]]

```clojure
(defworkflow my-workflow
    [{:keys [foo]}]
    ...)

(let [w (create-workflow client my-workflow {:task-queue ::my-task-queue})]
   (start w {:foo \"bar\"}))
```
"
  [name params* & body]
  (let [fqn (u/get-fq-classname name)
        sname (str name)]
    (if (= (count params*) 2)                               ;; legacy
      (do
        (println (str *file* ":" (:line (meta &form)) " WARN: (defworkflow " name ") with [ctx {:keys [signals args]}] is deprecated.  Use [args] form."))
        `(def ~name ^{::w/def {:name ~sname :fqn ~fqn :type :legacy}}
           (fn [ctx# args#]
             (log/trace (str ~fqn ": ") args#)
             (let [f# (fn ~params* (do ~@body))]
               (f# ctx# args#)))))
      (do
        `(def ~name ^{::w/def {:name ~sname :fqn ~fqn}}
           (fn [args#]
             (log/trace (str ~fqn ": ") args#)
             (let [f# (fn ~params* (do ~@body))]
               (f# args#))))))))
