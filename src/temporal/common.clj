;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.common
  (:require [temporal.internal.utils :as u])
  (:import [io.temporal.common RetryOptions RetryOptions$Builder]))

(def retry-options
  "
| Value                     | Description                                                                 | Type            | Default |
| ------------------------- | --------------------------------------------------------------------------- | ------------    | ------- |
| :initial-interval         | Interval of the first retry.                                                | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :backoff-coefficient      | Coefficient used to calculate the next retry interval.                      | double          | 0.0     |
| :maximum-attempts         | When exceeded the amount of attempts, stop.                                 | int             | 0 (unlimited) |
| :maximum-interval         | Maximum interval between retries.                                           | [Duration](https://docs.oracle.com/javase/8/docs/api//java/time/Duration.html) | |
| :do-not-retry             | List of application failures types to not retry.                            | list of strings | []      |
"
  {:initial-interval    #(.setInitialInterval ^RetryOptions$Builder %1 %2)
   :backoff-coefficient #(.setBackoffCoefficient ^RetryOptions$Builder %1 %2)
   :maximum-attempts    #(.setMaximumAttempts ^RetryOptions$Builder %1 %2)
   :maximum-interval    #(.setMaximumInterval ^RetryOptions$Builder %1 %2)
   :do-not-retry        #(.setDoNotRetry ^RetryOptions$Builder %1 (into-array String %2))})

(defn ^:no-doc retry-options->
  ^RetryOptions [params]
  (u/build (RetryOptions/newBuilder (RetryOptions/getDefaultInstance)) retry-options params))
