(ns temporal.converter.default-test
  (:require
   [clojure.test :refer [are deftest is]]
   [taoensso.nippy :as nippy]
   [temporal.converter.default :as sut]
   [temporal.converter.metadata :refer [->metadata]]
   [temporal.converter.payload :refer [->payload]])
  (:import
   [io.temporal.api.common.v1 Payload]
   [io.temporal.common.converter DataConverterException EncodingKeys NullPayloadConverter]))

(defn payload->encoding [^Payload payload]
  (.. payload
      (getMetadataOrThrow EncodingKeys/METADATA_ENCODING_KEY)
      (toStringUtf8)))

(defn null-payload? [^Payload payload]
  (and (= "binary/null" (payload->encoding payload))
       (.. payload (getData) (isEmpty))))

(defn nippy-payload? [expected]
  (fn [^Payload payload]
    (and (= "binary/plain" (payload->encoding payload))
         (= expected (-> payload
                         (.. (getData) (toByteArray))
                         nippy/thaw)))))

(deftest to-payload-with-default-converters
  (let [data-converter (sut/create)]
    (are [predicate input-value] (-> data-converter
                                     (.toPayload input-value)
                                     (.get)
                                     (predicate))
      null-payload? nil
      (nippy-payload? :something) :something
      (nippy-payload? "Something") "Something"
      (nippy-payload? [1 2 3 4]) [1 2 3 4]
      (nippy-payload? {:foo "bar"}) {:foo "bar"})))

(deftest to-payload-with-custom-converters
  (let [data-converter (sut/create [(NullPayloadConverter.)])]
    (is (null-payload? (.. data-converter (toPayload nil) (get))))

    (are [input-value] (thrown-with-msg? DataConverterException
                                         #"No PayloadConverter is registered with this DataConverter"
                                         (.toPayload data-converter input-value))
      :something
      "Something"
      [1 2 3 4]
      {:foo "bar"})))

(def metadata-null
  (->metadata "binary/null"))

(def metadata-plain
  (->metadata "binary/plain"))

(def metadata-json
  (->metadata "json/plain"))

(defn ->payload-json [input]
  (->payload input metadata-json))

(defn ->payload-nippy [input]
  (-> input (nippy/freeze) (->payload metadata-plain)))

(deftest from-payload-with-default-converters
  (are [expected payload] (-> (sut/create)
                              (.fromPayload payload Object Object)
                              (= expected))
    nil
    (->payload nil metadata-null)

    nil
    (->payload-nippy nil)

    :something
    (->payload-nippy :something)

    1234
    (->payload-nippy 1234)

    [1 2 3 4]
    (->payload-nippy [1 2 3 4])

    {:foo :bar}
    (->payload-nippy {:foo :bar})

    nil
    (->payload-json "null")

    1234
    (->payload-json "1234")

    (hash-map :foo "bar")
    (->payload-json "{\"foo\":\"bar\"}")

    [1 2 3 4]
    (->payload-json "[1,2,3,4]")

    "Raw JSON string"
    (->payload-json "\"Raw JSON string\"\"")))
