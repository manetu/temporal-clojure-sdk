;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.promise
  "Methods for managing promises from pending activities from within workflows"
  (:require [promesa.core :as p]
            [temporal.internal.promise :as pt])
  (:import [temporal.internal.promise PromiseAdapter]
           [io.temporal.workflow Workflow Promise]))

(defn- ->array
  ^"[Lio.temporal.workflow.Promise;" [coll]
  {:pre [(every? (partial instance? PromiseAdapter) coll)]}
  (into-array Promise (map #(.p %) coll)))

(defn all
  "Returns Promise that becomes completed when all arguments are completed.

*N.B. A single promise failure causes resulting promise to deliver the failure immediately.*

Similar to [promesa/all](https://funcool.github.io/promesa/latest/promesa.core.html#var-all) but designed to work with
promises returned from [[temporal.activity/invoke]] from within workflow context.

```clojure
(-> (all [(a/invoke activity-a ..) (a/invoke activity-b ..)])
    (promesa.core/then (fn [[a-result b-result]] ...)))
```
"
  [coll]
  (-> (pt/->PromiseAdapter (Promise/allOf (->array coll)))
      (p/then (fn [_]
                (mapv deref coll)))))

(defn all-settled
  "Returns a Promise that becomes completed/failed when all the arguments are done/settled, even in the face of errors.

*N.B. You must handle the exceptions in the returned promise with promesa*

Similar to [promesa/all](https://funcool.github.io/promesa/latest/promesa.core.html#var-all) but designed to work with
promises returned from [[temporal.activity/invoke]] from within workflow context.

For more Java SDK samples example look here:
   https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/batch

```clojure
(-> (all-settled [(a/invoke activity-a ..) (a/invoke activity-b ..)])
    (promesa.core/then (fn [[a-result b-result]] ...)))
```
"
  [coll]
  (letfn [(wait! [^Promise p] (try (.get p) (catch Exception _)))]
    ;; So we do not have to duplicate this, make the only copy here
    (let [promises (->array coll)]
      (run! wait! promises)
      (->
       (Promise/allOf promises)
       (pt/->PromiseAdapter)
       ;; The promises are all completed at this point,
       ;; this is just to use the promesa library
       (p/then (fn [_] (mapv deref coll)))))))

(defn race
  "Returns Promise that becomes completed when any of the arguments are completed.

*N.B. A single promise failure causes resulting promise to deliver the failure immediately.*

Similar to [promesa/race](https://funcool.github.io/promesa/latest/promesa.core.html#var-race) but designed to work with
promises returned from [[temporal.activity/invoke]] from within workflow context.

```clojure
(-> (race [(a/invoke activity-a ..) (a/invoke activity-b ..)])
    (promesa.core/then (fn [fastest-result] ...)))
```
"
  [coll]
  (pt/->PromiseAdapter (Promise/anyOf (->array coll))))

(defn resolved
  "Returns a new, fully resolved promise"
  [value]
  (pt/->PromiseAdapter (Workflow/newPromise value)))

(defn rejected
  "Returns a new, rejected promise"
  [^Exception e]
  (pt/->PromiseAdapter (Workflow/newFailedPromise e)))
