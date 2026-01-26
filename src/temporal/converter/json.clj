(ns temporal.converter.json
  (:require
   [jsonista.core :as json]
   [temporal.converter.metadata :refer [->metadata]]
   [temporal.converter.optional :refer [->optional]]
   [temporal.converter.payload :refer [->payload]])
  (:import
   [io.temporal.api.common.v1 Payload]
   [io.temporal.common.converter PayloadConverter]
   [java.lang.reflect Type]))

(def ^String json-plain
  "json/plain")

(def metadata
  (->metadata json-plain))

(def default-object-mapper
  json/keyword-keys-object-mapper)

(defn create ^PayloadConverter
  ([]
   (create default-object-mapper))

  ([object-mapper]
   (reify PayloadConverter
     (getEncodingType [_]
       json-plain)

     (toData [_ value]
       (-> value
           (json/write-value-as-bytes object-mapper)
           (->payload metadata)
           (->optional)))

     (fromData [_ ^Payload content ^Class _value-class ^Type _value-type]
       (-> content
           (.getData)
           (.toByteArray)
           (json/read-value object-mapper))))))
