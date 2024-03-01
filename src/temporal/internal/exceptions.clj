;; Copyright © 2024 Manetu, Inc.  All rights reserved

(ns temporal.internal.exceptions
  (:require [slingshot.slingshot :refer [throw+]]
            [temporal.exceptions :as e]
            [temporal.internal.utils :as u])
  (:import [io.temporal.failure ApplicationFailure]))

(def exception-type (name ::slingshot))

(defn slingshot? [ex]
  (and (instance? ApplicationFailure (ex-cause ex))
       (= exception-type (.getType (cast ApplicationFailure (ex-cause ex))))))

(defn freeze
  [{{:keys [::e/non-retriable?] :or {non-retriable? false}} :object :as context}]
  (let [o (u/->objarray context)
        t (if non-retriable?
            (ApplicationFailure/newNonRetryableFailure nil exception-type o)
            (ApplicationFailure/newFailure nil exception-type o))]
    (throw t)))

(defn recast-stone [ex]
  (let [stone (->> ex ex-cause (cast ApplicationFailure) (.getDetails) u/->args :object)]
    (throw+ stone)))                                        ;; FIXME: Does not preserve the original stack-trace
