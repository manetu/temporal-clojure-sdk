;; Copyright © 2024 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.exceptions
  (:require [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as log]
            [temporal.exceptions :as e]
            [temporal.internal.utils :as u])
  (:import [io.temporal.failure ApplicationFailure]))

(def exception-type (name ::slingshot))

(defn slingshot? [ex]
  (let [^ApplicationFailure cause (ex-cause ex)]
    (and (instance? ApplicationFailure cause)
         (= exception-type (.getType cause)))))

(defn forward
  [{:keys [message wrapper] {:keys [::e/non-retriable?] :or {non-retriable? false} :as object} :object :as context}]
  (log/trace "forward:" context)
  (cond
    (and (map? object) (empty? object) (true? (some->> wrapper (instance? Throwable))))
    (do
      (log/trace "recasting wrapped exception")
      (throw wrapper))

    (instance? Throwable object)
    (do
      (log/trace "recasting throwable")
      (throw object))

    :else
    (do
      (log/trace "recasting stone within ApplicationFailure")
      (let [o (u/->objarray context)
            t (if non-retriable?
                (ApplicationFailure/newNonRetryableFailure message exception-type o)
                (ApplicationFailure/newFailure message exception-type o))]
        (throw t)))))

(defn recast-stone [ex]
  (let [^ApplicationFailure af (->> ex ex-cause (cast ApplicationFailure))
        stone (->> (.getDetails af) u/->args :object)]
    (throw+ stone)))                                        ;; FIXME: Does not preserve the original stack-trace
