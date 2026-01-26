(ns temporal.converter.byte-string
  (:import
   [io.temporal.shaded.com.google.protobuf ByteString]
   [java.nio.charset StandardCharsets]))

(defprotocol ToByteString
  (->byte-string ^ByteString [value]))

(extend-protocol ToByteString
  nil
  (->byte-string [_]
    ByteString/EMPTY)

  byte/1
  (->byte-string [value]
    (ByteString/copyFrom value))

  String
  (->byte-string [value]
    (ByteString/copyFrom value StandardCharsets/UTF_8))

  ByteString
  (->byte-string [value]
    value))

(defn byte-string? [input]
  (instance? ByteString input))
