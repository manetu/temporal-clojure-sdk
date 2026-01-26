(ns temporal.converter.payload
  (:require
   [temporal.converter.byte-string :refer [->byte-string]])
  (:import
   [io.temporal.api.common.v1 Payload]))

(def empty-metadata {})

(defn ->payload
  (^Payload [value]
   (->payload value empty-metadata))

  (^Payload [value metadata]
   (.. (Payload/newBuilder)
       (putAllMetadata metadata)
       (setData (->byte-string value))
       (build))))
