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

(def ^Field converters-map-field (get-field "convertersMap"))

(def ^Field serialization-context-field (get-field "serializationContext"))

(defn encoded->payload
  "This is a custom re-implementation of the PayloadAndFailureDataConverter/toPayload"
  [^PayloadAndFailureDataConverter data-converter {:keys [value encoding]}]
  (let [converters-map (.get converters-map-field data-converter)
        converter (.get ^Map converters-map encoding)]
    (when-not converter
      (throw (DataConverterException.
              (str "No PayloadConverter is registered for this encoding: " encoding))))

    (let [serialization-context (get serialization-context-field data-converter)
          converter (if serialization-context
                      (.withContext ^PayloadConverter converter serialization-context)
                      converter)
          result (.toData ^PayloadConverter converter value)]
      (when-not (.isPresent result)
        (throw (DataConverterException.
                (str "Cannot encode value with the selected converter:" value))))

      result)))

(defn create
  (^DefaultDataConverter []
   (create standard-converters))

  (^DefaultDataConverter [converters]
   (proxy [DefaultDataConverter] [(into-array PayloadConverter converters)]
     (toPayload [value]
       (let [^PayloadAndFailureDataConverter this this]
         (if (encoding/tagged? value)
           (encoded->payload this value)
           (proxy-super toPayload value)))))))
