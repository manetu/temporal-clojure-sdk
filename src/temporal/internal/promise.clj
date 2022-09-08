;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.promise
  (:require [promesa.protocols :as pt]
            [temporal.internal.utils :refer [->Func] :as u])
  (:import [clojure.lang IDeref IBlockingDeref]
           [io.temporal.workflow Promise]
           [java.util.concurrent CompletableFuture]))

(deftype PromiseAdapter [^Promise p]
  IDeref
  (deref [_] (.get p))
  IBlockingDeref
  (deref [_ ms val] (.get p ms val)))

(defmulti ->temporal type)

(defmethod ->temporal Promise
  [x] x)

(defmethod ->temporal CompletableFuture
  [^CompletableFuture x]
  (reify Promise
    (get [_] (.get x))
    (isCompleted [_] (.isDone x))
    (handle [_ f]
      (try
        (let [r (.get x)]
          (.apply f r nil))
        (catch Exception e
          (.apply f nil e))))))

(defmethod ->temporal :default
  [x]
  (reify Promise
    (get [_] x)
    (handle [_ f]
      (.apply f x nil))
    (isCompleted [_] true)))

(def fw-identity (->Func identity))

(extend-protocol pt/IPromise
  PromiseAdapter
  (-map
    ([it f]
     (.thenApply ^Promise (.p it) (->Func f)))

    ([it f executor]))

  (-bind
    ([it f]
     (.thenCompose ^Promise (.p it) (->Func f)))

    ([it f executor]))

  (-then
    ([it f]
     (pt/-promise (.thenCompose ^Promise (.p it) (->Func (comp ->temporal f)))))

    ([it f executor]))

  (-mapErr
    ([it f]
     (letfn [(handler [e]
               (if (instance? Promise e)
                 (f (.getCause ^Exception e))
                 (f e)))]
       (.exceptionally ^Promise (.p it) (->Func handler))))

    ([it f executor]))

  (-thenErr
    ([it f]
     (letfn [(handler [v e]
               (if e
                 (if (instance? Promise e)
                   (pt/-promise (f (.getFailure e)))
                   (pt/-promise (f e)))
                 it))]
       (as-> ^Promise (.p it) $$
         (.handle $$ (->Func handler))
         (.thenCompose $$ fw-identity))))

    ([it f executor]))

  (-handle
    ([it f]
     (as-> ^Promise (.p it) $$
       (.handle $$ (->Func (comp ->temporal f)))
       (.thenCompose $$ fw-identity)))

    ([it f executor]))

  (-finally
    ([it f])

    ([it f executor])))

(extend-protocol pt/IPromiseFactory
  Promise
  (-promise [p] (->PromiseAdapter p))

  PromiseAdapter
  (-promise [p] p))
