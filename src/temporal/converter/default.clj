(ns temporal.converter.default
  (:require
   [clojure.stacktrace :as stacktrace]
   [temporal.converter.json :as json]
   [temporal.converter.nippy :as nippy])
  (:import
   [io.temporal.api.failure.v1 ApplicationFailureInfo Failure]
   [io.temporal.common.converter DataConverterException DefaultDataConverter NullPayloadConverter PayloadConverter]
   [io.temporal.common.converter DataConverter FailureConverter]
   [io.temporal.failure DefaultFailureConverter]
   [io.temporal.failure DefaultFailureConverter]
   [io.temporal.payload.context SerializationContext]
   [java.lang RuntimeException]))

(def standard-converters
  [(NullPayloadConverter.)
   (nippy/create)
   (json/create)])

;; DefaultFailureConverter can fail while trying to serialize failure details
;; through the application's DataConverter (for example when Nippy cannot
;; freeze ex-data). When that happens, fallback to a minimal Failure.
(defn- fallback-failure
  ^Failure [^Throwable throwable]
  (-> (Failure/newBuilder)
      (.setMessage (ex-message throwable))
      (.setStackTrace (with-out-str (stacktrace/print-stack-trace throwable)))
      (.setApplicationFailureInfo
       (-> (ApplicationFailureInfo/newBuilder)
           (.setType (.getName (class throwable)))
           ;; Mark it non-retryable so Temporal does not keep retrying the same
           ;; serialization failure.
           (.setNonRetryable true)))
      (.build)))

(defn create-failure
  ^FailureConverter []
  (let [^DefaultFailureConverter delegate (DefaultFailureConverter.)]
    (reify FailureConverter
      (^RuntimeException failureToException [_ ^Failure failure ^DataConverter data-converter]
        (.failureToException delegate failure data-converter))

      (^Failure exceptionToFailure [_ ^Throwable throwable ^DataConverter data-converter]
        (try
          (.exceptionToFailure delegate throwable data-converter)
          (catch DataConverterException ex
            ;; Only catch DataConverterException, this is only for serialization errors
            (fallback-failure ex))))

      (^FailureConverter withContext [_ ^SerializationContext _context]
        (create-failure)))))

(defn create
  (^DefaultDataConverter []
   (create standard-converters))

  (^DefaultDataConverter [converters]
   (doto (DefaultDataConverter. (into-array PayloadConverter converters))
     (.withFailureConverter (create-failure)))))
