;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.activity
  "Methods for defining and invoking activity tasks"
  (:require [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [promesa.core :as p]
            [temporal.internal.activity :as a]
            [temporal.internal.utils :refer [->promise] :as u])
  (:import [io.temporal.workflow Workflow]
           [io.temporal.activity ActivityOptions ActivityOptions$Builder]
           [java.time Duration]))

(def ^:no-doc invoke-option-spec
  {:start-to-close-timeout #(.setStartToCloseTimeout ^ActivityOptions$Builder %1  %2)})

(defn- invoke-options->
  ^ActivityOptions [params]
  (u/build (ActivityOptions/newBuilder) invoke-option-spec params))

(def ^:no-doc default-invoke-options {:start-to-close-timeout (Duration/ofSeconds 3)})

(defn invoke
  "
Invokes 'activity' with 'params' from within a workflow context.  Returns a promise that when derefed will resolve to
the evaluation of the defactivity once the activity concludes.

```clojure
(defactivity my-activity
   [ctx {:keys [foo] :as args}]
   ...)

(invoke my-activity {:foo \"bar\"} {:start-to-close-timeout (Duration/ofSeconds 3))
```
"
  ([activity params] (invoke activity params default-invoke-options))
  ([activity params options]
   (let [act-name (a/get-annotation activity)
         stub (Workflow/newUntypedActivityStub (invoke-options-> options))]
     (-> (->promise
          (.executeAsync stub act-name u/bytes-type (u/->objarray params)))
         (p/then nippy/thaw)))))

(defmacro defactivity
  "
Defines a new activity, similar to defn, expecting a 2-arity parameter list and body.  Should evaluate to something
serializable, which will be available to the [[invoke]] caller.

Arguments:

- `ctx`: Context passed through from [[temporal.client.worker/start]]
- `args`: Passed from 'params' of [[invoke]]

```clojure
(defactivity greet-activity
    [ctx {:keys [name] :as args}]
    (str \"Hi, \" name))
```
"
  [name params* & body]
  (let [class-name (u/get-classname name)]
    `(def ~name ^{::a/def ~class-name}
       (fn [ctx# args#]
         (log/trace (str ~class-name ": ") args#)
         (let [f# (fn ~params* (do ~@body))]
           (f# ctx# args#))))))
