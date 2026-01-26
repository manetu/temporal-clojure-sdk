(ns temporal.converter.payload-test
  (:require
   [clojure.test :refer [deftest is]]
   [temporal.converter.payload :as sut])
  (:import
   [java.nio.charset StandardCharsets]))

(deftest ->payload-without-metadata
  (let [payload (sut/->payload (.getBytes "Something" StandardCharsets/UTF_8))]
    (is (.. payload getMetadataMap isEmpty))))

(deftest ->payload-with-metadata
  (let [payload (sut/->payload (.getBytes "Something" StandardCharsets/UTF_8)
                               {"something" "bar"})]
    (is (not (.. payload getMetadataMap isEmpty)))))
