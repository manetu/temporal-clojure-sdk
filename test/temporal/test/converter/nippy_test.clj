(ns temporal.test.converter.nippy-test
  (:require [clojure.template :refer [do-template]]
            [clojure.test :refer [deftest is testing]]
            [taoensso.nippy :as nippy]
            [temporal.converter.byte-string :refer [byte-string?]]
            [temporal.converter.nippy :as sut]
            [temporal.converter.payload :refer [->payload]])
  (:import [io.temporal.api.common.v1 Payload]
           [io.temporal.common.converter DataConverterException]
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
   #{:a :b :c})

  (testing "serialization failures throw DataConverterException"
    (let [value (Object.)]
      (is (thrown? Exception (nippy/freeze value)))
      (is (thrown? DataConverterException
                   (.toData data-converter value))))))

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

(defn- non-serializable-ex
  [msg data]
  (let [handle (Object.)]
    (proxy [clojure.lang.ExceptionInfo] [msg data]
      (toString [] (str "NonSerializable: " msg " " handle)))))

(deftest non-serializable-throwable
  (testing "ExceptionInfo subclass with a non-serializable field"
    (let [ex (non-serializable-ex "boom" {:code "42601"})]
      (is (thrown? Exception (nippy/freeze ex)))
      (is (thrown? DataConverterException (.toData data-converter ex)))))
  (let [ex (ex-info "boom"
                    {:code "42601" :obj (Object.)}
                    (ex-info "cause" {:obj (Object.)}))]
    (testing "throwable with non-serializable ex-data throws DataConverterException"
      (is (thrown? DataConverterException (.toData data-converter ex))))
    (testing "throwable nested in a collection still fails"
      (is (thrown? DataConverterException (.toData data-converter {:error ex :n 1}))))
    (testing "non-throwable values that cannot be frozen still fail"
      (is (thrown? DataConverterException (.toData data-converter (Object.)))))))
