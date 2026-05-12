(ns temporal.converter.byte-string
  (:import
   [io.temporal.shaded.com.google.protobuf ByteString]
   [java.nio.charset StandardCharsets]))

(defprotocol ToByteString
  (->byte-string ^ByteString [value]))

(extend-protocol ToByteString
  ;; byte/1 - FIXME: this is not supported by lein-cloverage
  (Class/forName "[B")
  (->byte-string [value]
    (ByteString/copyFrom value))

  nil
  (->byte-string [_]
    ByteString/EMPTY)

  String
  (->byte-string [value]
    (ByteString/copyFrom value StandardCharsets/UTF_8))

  ByteString
  (->byte-string [value]
    value))

(defn byte-string? [input]
  (instance? ByteString input))
