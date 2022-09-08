;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.common
  (:require [temporal.internal.utils :as u])
  (:import [io.temporal.common RetryOptions RetryOptions$Builder]))

(def retry-option-spec
  {:initial-interval    #(.setInitialInterval ^RetryOptions$Builder %1 %2)
   :backoff-coefficient #(.setBackoffCoefficient ^RetryOptions$Builder %1 %2)
   :maximum-attempts    #(.setMaximumAttempts ^RetryOptions$Builder %1 %2)
   :maximum-interval    #(.setMaximumInterval ^RetryOptions$Builder %1 %2)
   :do-not-retry        #(.setDoNotRetry ^RetryOptions$Builder %1 (into-array String %2))})

(defn retry-options->
  ^RetryOptions [params]
  (u/build (RetryOptions/newBuilder) retry-option-spec params))

