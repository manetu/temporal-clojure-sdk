(ns temporal.converter.byte-string-test
  (:require
   [clojure.test :refer [are deftest]]
   [temporal.converter.byte-string :as sut])
  (:import
   [io.temporal.shaded.com.google.protobuf ByteString]
   [java.nio.charset StandardCharsets]))

(deftest ->byte-string
  (are [input] (->> input (sut/->byte-string) (instance? ByteString))
    nil
    "Something"
    (byte-array 10)
    ByteString/EMPTY))

(deftest byte-string?
  (are [input] (sut/byte-string? input)
    ByteString/EMPTY
    (ByteString/copyFrom "" StandardCharsets/UTF_8)
    (ByteString/copyFrom (byte-array 0))
    (ByteString/copyFrom (byte-array 10)))

  (are [input] (not (sut/byte-string? input))
    nil
    ""
    (byte-array 0)))
