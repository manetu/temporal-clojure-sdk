(ns temporal.converter.default
  (:require
   [temporal.converter.encoding :as encoding]
   [temporal.converter.json :as json]
   [temporal.converter.nippy :as nippy])
  (:import
   [io.temporal.common.converter
    DataConverterException
    DefaultDataConverter
    NullPayloadConverter
    PayloadAndFailureDataConverter
    PayloadConverter]
   [java.lang.reflect Field]
   [java.util Map]))

(set! *warn-on-reflection* true)

(def standard-converters
  [(NullPayloadConverter.)
   (nippy/create)
   (json/create)])

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
      (if-let [serialization-context (get serialization-context-field data-converter)]
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
   (proxy [DefaultDataConverter] [(into-array PayloadConverter converters)]
     (withContext [context]
       (let [data-converter (create converters this)]
         (.set serialization-context-field data-converter context)
         data-converter))

     (toPayload [value]
       (let [^PayloadAndFailureDataConverter this this]
         (if (encoding/tagged? value)
           (encoded->payload this value)
           (proxy-super toPayload value)))))))
