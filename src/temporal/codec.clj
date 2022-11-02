;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.codec
  "Methods for managing codecs between a client and the Temporal backend"
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [medley.core :as m])
  (:import [io.temporal.common.converter DefaultDataConverter CodecDataConverter]
           [io.temporal.payload.codec PayloadCodec]
           [io.temporal.api.common.v1 Payload]
           [com.google.protobuf ByteString]
           [java.util Collections]))

(defprotocol Codec
  "A protocol for encoding/decoding of 'payload' maps, suitable for use with [[create]].

  'payload' is a map consisting of :metadata and :data, where :metadata is a map of string/bytes pairs
  and :data is bytes.  The codec may choose to transform or encapsulate the input payload and return
  a new payload, potentially with different data/metadata."
  (decode [this payload])
  (encode [this payload]))

(extend-protocol p/Datafiable
  Payload
  (datafy [d]
    {:metadata (->> (.getMetadataMap d)
                    (into {})
                    (m/map-vals #(.toByteArray %)))
     :data     (-> (.getData d)
                   (.toByteArray))}))

(defn- ^:no-doc payload->
  [x]
  (d/datafy x))

(defn- ^:no-doc ->payload
  [{:keys [metadata data] :as payload}]
  (let [builder (Payload/newBuilder)]
    (run! (fn [[k v]] (.putMetadata builder k (ByteString/copyFrom (bytes v)))) metadata)
    (when (some? data)
      (.setData builder (ByteString/copyFrom (bytes data))))
    (.build builder)))

(defn- ^:no-doc codec-map
  [f payloads]
  (map (fn [payload]
         (-> payload
             (payload->)
             (f)
             (->payload)))
       payloads))

(defn- ^:no-doc -encode [codec payloads]
  (codec-map (partial encode codec) payloads))

(defn- ^:no-doc -decode [codec payloads]
  (codec-map (partial decode codec) payloads))

(defn create
  "Creates an instance of a [DataConverter](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/common/converter/DataConverter.html)
  that accepts a [[Codec]]"
  ^CodecDataConverter [codec]
  (CodecDataConverter.
   (DefaultDataConverter/newDefaultInstance)
   (Collections/singletonList
    (reify PayloadCodec
      (encode [_ payloads]
        (-encode codec payloads))
      (decode [_ payloads]
        (-decode codec payloads))))))
