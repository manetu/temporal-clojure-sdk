;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.promise
  (:require [promesa.protocols :as pt]
            [temporal.internal.utils :refer [->Func] :as u])
  (:import [clojure.lang IDeref IBlockingDeref]
           [io.temporal.workflow Promise]
           [java.util.concurrent CompletableFuture]
           [java.util.function BiFunction]))

(deftype PromiseAdapter [^Promise p]
  IDeref
  (deref [_] (.get p))
  IBlockingDeref
  (deref [_ ms val] (.get p ms val)))

(deftype BiFunctionWrapper [f]
  BiFunction
  (apply [_ a b]
    (f a b)))

(defmulti ->temporal type)

(defmethod ->temporal Promise
  [x] x)

(defmethod ->temporal PromiseAdapter
  [x] (.p x))

(defmethod ->temporal CompletableFuture
  [^CompletableFuture x]
  (reify Promise
    (get [_] (.get x))
    (handle [_ f]
      (.handle x (->BiFunctionWrapper (fn [v e] (.apply f v e)))))
    (isCompleted [_] (.isDone x))))

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

  ;; -fmap: Apply function to value (no flattening)
  ;; Replaces v9 -map
  (-fmap
    ([it f]
     (pt/-promise (.thenApply ^Promise (.p it) (->Func (comp ->temporal f)))))
    ([it f executor]
     (pt/-fmap it f)))

  ;; -mcat: Monadic bind with one-level flatten
  ;; Replaces v9 -bind
  (-mcat
    ([it f]
     (pt/-promise (.thenCompose ^Promise (.p it) (->Func (comp ->temporal f)))))
    ([it f executor]
     (pt/-mcat it f)))

  ;; -then: Apply with multi-level flatten (same as v9)
  (-then
    ([it f]
     (pt/-promise (.thenCompose ^Promise (.p it) (->Func (comp ->temporal f)))))
    ([it f executor]
     (pt/-then it f)))

  ;; -merr: Error mapping with flatten
  ;; Combines old v9 -mapErr and -thenErr behavior
  (-merr
    ([it f]
     (letfn [(handler [v e]
               (if e
                 (let [cause (if (instance? Promise e)
                               (.getFailure e)
                               e)]
                   (->temporal (f cause)))
                 (.p it)))]
       (as-> ^Promise (.p it) $$
         (.handle $$ (->Func handler))
         (.thenCompose $$ fw-identity)
         (pt/-promise $$))))
    ([it f executor]
     (pt/-merr it f)))

  ;; -hmap: Handle both success and failure
  ;; Replaces v9 -handle
  (-hmap
    ([it f]
     (as-> ^Promise (.p it) $$
       (.handle $$ (->Func (comp ->temporal f)))
       (.thenCompose $$ fw-identity)
       (pt/-promise $$)))
    ([it f executor]
     (pt/-hmap it f)))

  ;; -fnly: Finally handler (return value ignored)
  ;; Replaces v9 -finally (previously stubbed)
  (-fnly
    ([it f]
     (as-> ^Promise (.p it) $$
       (.handle $$ (->Func (fn [v e] (f v e) (if e (throw e) v))))
       (.thenCompose $$ fw-identity)
       (pt/-promise $$)))
    ([it f executor]
     (pt/-fnly it f))))

(extend-protocol pt/IPromiseFactory
  Promise
  (-promise [p] (->PromiseAdapter p))

  PromiseAdapter
  (-promise [p] p))
