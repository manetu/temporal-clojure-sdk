(ns temporal.converter.metadata-test
  (:require
   [clojure.test :refer [are deftest is]]
   [temporal.converter.byte-string :refer [->byte-string]]
   [temporal.converter.metadata :as sut]))

(deftest ->metadata-test
  (are [input] (is (sut/metadata? (sut/->metadata input)))
    nil
    {}
    "binary/null"))

(deftest metadata?
  (are [input] (is (sut/metadata? input))
    {}
    sut/empty
    {"custom" (->byte-string "data")})

  (are [input] (is (not (sut/metadata? input)))
    nil
    "Something"
    {:foo :bar}))
