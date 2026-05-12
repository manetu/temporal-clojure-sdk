(ns temporal.converter.nippy-test
  (:require
   [clojure.template :refer [do-template]]
   [clojure.test :refer [deftest is testing]]
   [taoensso.nippy :as nippy]
   [temporal.converter.byte-string :refer [byte-string?]]
   [temporal.converter.nippy :as sut]
   [temporal.converter.payload :refer [->payload]])
  (:import
   [io.temporal.api.common.v1 Payload]
   [java.util Optional]))

(def data-converter (sut/create))

(deftest getEncodingType
  (is (= "binary/plain" (.getEncodingType data-converter))))

(deftest toData
  (do-template
   [input-value]

   (testing (str "encoding: " (class input-value))
     (let [value (.toData data-converter input-value)
           payload (.get value)
           encoding (.getMetadataOrDefault payload "encoding" nil)
           data (.getData payload)
           bytes (.toByteArray data)]
       (is (instance? Optional value))
       (is (.isPresent value))
       (is (instance? Payload payload))
       (is (byte-string? encoding))
       (is (= "binary/plain" (.toStringUtf8 encoding)))
       (is (byte-string? data))
       (is (bytes? bytes))
       (is (= input-value (nippy/thaw bytes)))))
   nil
   "string"
   {:key "value"}
   [:a :b :c]
   #{:a :b :c}))

(deftest fromData
  (do-template
   [input-value]

   (testing (str "decoding: " (class input-value))
     (let [content (-> input-value (nippy/freeze) (->payload sut/metadata))]
       (is (= input-value (.fromData data-converter content (class input-value) Object)))))
   nil
   "string"
   {:key "value"}
   [:a 0 :b 1 :c 2]
   #{:a :b :c}))
