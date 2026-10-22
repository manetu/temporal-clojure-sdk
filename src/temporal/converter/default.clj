(ns temporal.converter.default
  (:require
   [clojure.stacktrace :as stacktrace]
   [temporal.converter.encoding :as encoding]
   [temporal.converter.json :as json]
   [temporal.converter.nippy :as nippy])
  (:import
   [io.temporal.api.failure.v1 ApplicationFailureInfo Failure]
   [io.temporal.common.converter
    DataConverter
    DataConverterException
    DefaultDataConverter
    FailureConverter
    NullPayloadConverter
    PayloadAndFailureDataConverter
    PayloadConverter]
   [io.temporal.failure DefaultFailureConverter]
   [io.temporal.payload.context SerializationContext]
   [java.lang RuntimeException]
   [java.lang.reflect Field]
   [java.util Map]))

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

(defn get-field ^Field [field]
  (doto (.getDeclaredField PayloadAndFailureDataConverter field)
    (.setAccessible true)))

(def ^Field converters-field (get-field "converters"))

(def ^Field converters-map-field (get-field "convertersMap"))

(def ^Field failure-converter-field (get-field "failureConverter"))

(def ^Field serialization-context-field (get-field "serializationContext"))

(defn get-converter [^PayloadAndFailureDataConverter data-converter encoding]
  (let [converters-map (.get converters-map-field data-converter)
        converter (.get ^Map converters-map encoding)]
    (when converter
      (if-let [serialization-context (.get serialization-context-field data-converter)]
        (.withContext ^PayloadConverter converter serialization-context)
        converter))))

(defn encoded->payload
  "This is a custom re-implementation of the PayloadAndFailureDataConverter/toPayload"
  [^PayloadAndFailureDataConverter data-converter {:keys [value encoding]}]
  (if-let [converter (get-converter data-converter encoding)]
    (let [result (.toData ^PayloadConverter converter value)]
      (if (.isPresent result)
        result
        (throw (DataConverterException.
                (str "Cannot encode value with the selected converter: " value)))))
    (throw (DataConverterException.
            (str "No PayloadConverter is registered for this encoding: " encoding)))))

(defn create
  (^DefaultDataConverter []
   (create standard-converters))

  (^DefaultDataConverter [converters source-data-converter]
   (let [data-converter (create converters)]
     (doseq [^Field field [converters-field converters-map-field failure-converter-field]]
       (->> (.get field source-data-converter)
            (.set field data-converter)))
     data-converter))

  (^DefaultDataConverter [converters]
   (let [^DefaultDataConverter data-converter
         (proxy [DefaultDataConverter] [(into-array PayloadConverter converters)]
           (withContext [context]
             (let [data-converter (create converters this)]
               (.set serialization-context-field data-converter context)
               data-converter))

           (toPayload [value]
             (let [^PayloadAndFailureDataConverter this this]
               (if (encoding/tagged? value)
                 (encoded->payload this value)
                 (proxy-super toPayload value)))))]
     (doto data-converter
       (.withFailureConverter (create-failure))))))
