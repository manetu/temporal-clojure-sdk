(ns temporal.converter.default
  (:require
   [temporal.converter.json :as json]
   [temporal.converter.nippy :as nippy])
  (:import
   [io.temporal.common.converter DefaultDataConverter NullPayloadConverter PayloadConverter]))

(def standard-converters
  [(NullPayloadConverter.)
   (nippy/create)
   (json/create)])

(defn create
  (^DefaultDataConverter []
   (create standard-converters))

  (^DefaultDataConverter [converters]
   (DefaultDataConverter.
    (into-array PayloadConverter converters))))
