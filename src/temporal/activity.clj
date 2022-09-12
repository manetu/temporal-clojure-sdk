;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.activity
  "Methods for defining and invoking activity tasks"
  (:require [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [promesa.core :as p]
            [temporal.internal.activity :as a]
            [temporal.internal.utils :as u]
            [temporal.internal.promise])                    ;; needed for IPromise protocol extention
  (:import [io.temporal.workflow Workflow]
           [java.time Duration]))

(def ^:no-doc default-invoke-options {:start-to-close-timeout (Duration/ofSeconds 3)})

(defn- complete-invoke
  [activity result]
  (log/trace activity "completed with" (count result) "bytes")
  (let [r (nippy/thaw result)]
    (log/trace activity "results:" r)
    r))

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
         stub (Workflow/newUntypedActivityStub (a/invoke-options-> options))]
     (log/trace "invoke:" activity "with" params options)
     (-> (.executeAsync stub act-name u/bytes-type (u/->objarray params))
         (p/then (partial complete-invoke activity))
         (p/catch (fn [e]
                    (log/error e)
                    (throw e)))))))

(defn local-invoke
  "
Invokes 'activity' with 'params' from within a workflow context as a [Local Activity](https://docs.temporal.io/concepts/what-is-a-local-activity/).  Returns a promise that when
derefed will resolve to the evaluation of the defactivity once the activity concludes.

```clojure
(defactivity my-activity
   [ctx {:keys [foo] :as args}]
   ...)

(local-invoke my-activity {:foo \"bar\"} {:start-to-close-timeout (Duration/ofSeconds 3))
```
"
  ([activity params] (invoke activity params default-invoke-options))
  ([activity params options]
   (let [act-name (a/get-annotation activity)
         stub (Workflow/newUntypedLocalActivityStub (a/local-invoke-options-> options))]
     (log/trace "local-invoke:" activity "with" params options)
     (-> (.executeAsync stub act-name u/bytes-type (u/->objarray params))
         (p/then (partial complete-invoke activity))
         (p/catch (fn [e]
                    (log/error e)
                    (throw e)))))))

(defmacro defactivity
  "
Defines a new activity, similar to defn, expecting a 2-arity parameter list and body.  Should evaluate to something
serializable, which will be available to the [[invoke]] caller, or to a core.async channel (See Async Mode below).

#### Async Mode:
Returning a core.async channel places the activity into
[Asynchronous](https://docs.temporal.io/java/activities/#asynchronous-activity-completion) mode, where the result may
be resolved at a future time by sending a single message on the channel.  Sending a
[Throwable](https://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html) will signal a failure of the activity.
Any other value will be serialized and returned to the caller.

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
  (let [fqn (u/get-fq-classname name)
        sname (str name)]
    `(def ~name ^{::a/def {:name ~sname :fqn ~fqn}}
       (fn [ctx# args#]
         (log/trace (str ~fqn ": ") args#)
         (let [f# (fn ~params* (do ~@body))]
           (f# ctx# args#))))))
