(ns temporal.converter.metadata
  (:refer-clojure :exclude [empty])
  (:require
   [temporal.converter.byte-string :refer [->byte-string byte-string?]])
  (:import
   [clojure.lang MapEntry]
   [io.temporal.common.converter EncodingKeys]
   [java.util Map]))

(def empty {})

(defn metadata-entry? [input]
  (and (instance? MapEntry input)
       (instance? String (key input))
       (byte-string? (val input))))

(defn metadata? [input]
  (and (instance? Map input)
       (every? metadata-entry? input)))

(defprotocol ToMetadata
  (->metadata ^Map [value]))

(extend-protocol ToMetadata
  nil
  (->metadata [_]
    empty)

  String
  (->metadata [encoding]
    (->metadata {EncodingKeys/METADATA_ENCODING_KEY encoding}))

  Map
  (->metadata [map]
    (reduce-kv (fn [m k v]
                 (assoc m (str k) (->byte-string v)))
               {}
               map)))
