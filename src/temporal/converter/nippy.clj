(ns temporal.converter.nippy
  (:require
   [taoensso.nippy :as nippy]
   [temporal.converter.metadata :refer [->metadata]]
   [temporal.converter.optional :refer [->optional]]
   [temporal.converter.payload :refer [->payload]])
  (:import
   [io.temporal.api.common.v1 Payload]
   [io.temporal.common.converter PayloadConverter]
   [java.lang.reflect Type]))

(def ^String binary-plain
  "binary/plain")

(def metadata
  (->metadata binary-plain))

(defn create ^PayloadConverter []
  (reify PayloadConverter
    (getEncodingType [_]
      binary-plain)

    (toData [_ value]
      (-> value (nippy/freeze) (->payload metadata) (->optional)))

    (fromData [_ ^Payload content ^Class _value-class ^Type _value-type]
      (-> content (.getData) (.toByteArray) (nippy/thaw)))))
