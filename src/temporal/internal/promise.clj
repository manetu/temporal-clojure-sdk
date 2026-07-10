;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.promise
  (:require [promesa.protocols :as pt]
            [temporal.internal.utils :refer [->Func] :as u])
  (:import [clojure.lang IDeref IBlockingDeref]
           [io.temporal.workflow Promise]
           [java.time Duration]
           [java.util.concurrent CompletableFuture CompletionStage TimeUnit]
           [java.util.function BiFunction Function]))

(declare ->temporal)

(deftype PromiseAdapter [^Promise p]
  IDeref
  (deref [_] (.get p))
  IBlockingDeref
  (deref [_ ms val] (.get p ms val))
  CompletionStage
  (thenCompose [_ f]
    (pt/-promise
     (.thenCompose p (->Func (fn [v] (->temporal (.apply ^Function f v))))))))

(defmethod print-method PromiseAdapter
  [^PromiseAdapter x ^java.io.Writer w]
  (.write w (str "#<temporal/PromiseAdapter " (.p x) ">")))

(deftype BiFunctionWrapper [f]
  BiFunction
  (apply [_ a b]
    (f a b)))

(defmulti ->temporal type)

(defmethod ->temporal Promise
  [x] x)

(defmethod ->temporal PromiseAdapter
  [^PromiseAdapter x] (.p x))

(defmethod ->temporal CompletableFuture
  [^CompletableFuture x]
  (reify Promise
    (get [_] (.get x))
    (handle [_ f]
      (let [^BiFunction bf (->BiFunctionWrapper (fn [v e] (.apply f v e)))]
        (.handle x bf)))
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
    ([it f _executor]
     (pt/-fmap it f)))

  ;; -mcat: Monadic bind with one-level flatten
  ;; Replaces v9 -bind
  (-mcat
    ([it f]
     (pt/-promise (.thenCompose ^Promise (.p it) (->Func (comp ->temporal f)))))
    ([it f _executor]
     (pt/-mcat it f)))

  ;; -then: Apply with multi-level flatten (same as v9)
  (-then
    ([it f]
     (pt/-promise (.thenCompose ^Promise (.p it) (->Func (comp ->temporal f)))))
    ([it f _executor]
     (pt/-then it f)))

  ;; -merr: Error mapping with flatten
  ;; Combines old v9 -mapErr and -thenErr behavior
  (-merr
    ([it f]
     (letfn [(handler [_v e]
               (if e
                 (let [cause (if (instance? Promise e)
                               (let [^Promise p e] (.getFailure p))
                               e)]
                   (->temporal (f cause)))
                 (.p it)))]
       (as-> ^Promise (.p it) $$
         (.handle $$ (->Func handler))
         (.thenCompose $$ fw-identity)
         (pt/-promise $$))))
    ([it f _executor]
     (pt/-merr it f)))

  ;; -hmap: Handle both success and failure
  ;; Replaces v9 -handle
  (-hmap
    ([it f]
     (as-> ^Promise (.p it) $$
       (.handle $$ (->Func (comp ->temporal f)))
       (.thenCompose $$ fw-identity)
       (pt/-promise $$)))
    ([it f _executor]
     (pt/-hmap it f)))

  ;; -fnly: Finally handler (return value ignored)
  ;; Replaces v9 -finally (previously stubbed)
  (-fnly
    ([it f]
     (as-> ^Promise (.p it) $$
       (.handle $$ (->Func (fn [v e] (f v e) (if e (throw e) v))))
       (.thenCompose $$ fw-identity)
       (pt/-promise $$)))
    ([it f _executor]
     (pt/-fnly it f))))

(extend-protocol pt/IPromiseFactory
  Promise
  (-promise [p] (->PromiseAdapter p))

  PromiseAdapter
  (-promise [p] p))

;; IState/IAwaitable: introspection and blocking-await support, so promesa's
;; p/pending?, p/resolved?, p/rejected?, p/extract, and p/await!/p/await work
;; against Temporal-backed promises. NOTE: Promise.getFailure() blocks until
;; the promise completes, so it is only called after an isCompleted guard
;; (where it returns immediately). ICancellable is deliberately not
;; implemented: io.temporal.workflow.Promise has no external-cancel API
;; (cancellation happens via CancellationScope).
(extend-protocol pt/IState
  PromiseAdapter
  (-extract
    ([it] (pt/-extract it nil))
    ([it default]
     (let [^Promise p (.p it)]
       (if (.isCompleted p) (or (.getFailure p) (.get p)) default))))
  (-resolved? [it] (let [^Promise p (.p it)] (and (.isCompleted p) (nil? (.getFailure p)))))
  (-rejected? [it] (let [^Promise p (.p it)] (and (.isCompleted p) (some? (.getFailure p)))))
  (-pending? [it] (not (.isCompleted ^Promise (.p it)))))

(extend-protocol pt/IAwaitable
  PromiseAdapter
  (-await!
    ([it] (.get ^Promise (.p it)))
    ([it duration]
     (let [ms (if (instance? Duration duration) (inst-ms duration) duration)]
       (.get ^Promise (.p it) (long ms) TimeUnit/MILLISECONDS)))))
