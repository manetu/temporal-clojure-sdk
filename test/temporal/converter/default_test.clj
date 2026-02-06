(ns temporal.converter.default-test
  (:require
   [clojure.template :refer [do-template]]
   [clojure.test :refer [are deftest is testing]]
   [jsonista.core :as json]
   [taoensso.nippy :as nippy]
   [temporal.converter.default :as sut]
   [temporal.converter.encoding :as encoding]
   [temporal.converter.metadata :refer [->metadata]]
   [temporal.converter.payload :refer [->payload]])
  (:import
   [io.temporal.api.common.v1 Payload]
   [io.temporal.common.converter
    DataConverterException
    EncodingKeys
    NullPayloadConverter
    PayloadAndFailureDataConverter]
   [io.temporal.payload.context WorkflowSerializationContext]
   [java.lang.reflect Field]))

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
                         (nippy/thaw))))))

(defn json-payload? [expected]
  (fn [^Payload payload]
    (and (= "json/plain" (payload->encoding payload))
         (= expected (-> payload
                         (.. (getData) (toByteArray))
                         (json/read-value json/keyword-keys-object-mapper))))))

(def context
  (WorkflowSerializationContext. "test" "test"))

(deftest to-payload-with-default-converters
  (do-template
   [predicate input-value]
   (let [data-converter (sut/create)]
     (testing "withhout-context"
       (is (predicate (-> data-converter
                          (.toPayload input-value)
                          (.get)))))

     (testing "with-context"
       (let [data-converter (.withContext data-converter context)]
         (is (= context (.get sut/serialization-context-field data-converter)))
         (is (predicate (-> data-converter
                            (.toPayload input-value)
                            (.get)))))))
   null-payload? nil
   null-payload? (encoding/with nil :null)
   null-payload? (encoding/with nil "binary/null")

   (nippy-payload? :something) :something
   (nippy-payload? "Something") "Something"
   (nippy-payload? [1 2 3 4]) [1 2 3 4]
   (nippy-payload? {:foo "bar"}) {:foo "bar"}

   (nippy-payload? :something) (encoding/with :something :nippy)
   (nippy-payload? "Something") (encoding/with "Something" :nippy)
   (nippy-payload? [1 2 3 4]) (encoding/with [1 2 3 4] :nippy)
   (nippy-payload? {:foo "bar"}) (encoding/with {:foo "bar"} :nippy)

   (nippy-payload? :something) (encoding/with :something "binary/plain")
   (nippy-payload? "Something") (encoding/with "Something" "binary/plain")
   (nippy-payload? [1 2 3 4]) (encoding/with [1 2 3 4] "binary/plain")
   (nippy-payload? {:foo "bar"}) (encoding/with {:foo "bar"} "binary/plain")

   (json-payload? nil) (encoding/with nil :json)
   (json-payload? "Something") (encoding/with "Something" :json)
   (json-payload? [1 2 3 4]) (encoding/with [1 2 3 4] :json)
   (json-payload? {:foo "bar"}) (encoding/with {:foo "bar"} :json)

   (json-payload? nil) (encoding/with nil "json/plain")
   (json-payload? "Something") (encoding/with "Something" "json/plain")
   (json-payload? [1 2 3 4]) (encoding/with [1 2 3 4] "json/plain")
   (json-payload? {:foo "bar"}) (encoding/with {:foo "bar"} "json/plain")))

(deftest to-payload-with-custom-converters
  (let [data-converter (sut/create [(NullPayloadConverter.)])]
    (are [input-value] (null-payload? (.. data-converter (toPayload input-value) (get)))
      nil
      (encoding/with nil :null))

    (are [input-value] (thrown-with-msg? DataConverterException
                                         #"No PayloadConverter is registered with this DataConverter"
                                         (.toPayload data-converter input-value))
      :something
      "Something"
      [1 2 3 4]
      {:foo "bar"})

    (are [input-value] (thrown-with-msg? DataConverterException
                                         #"Cannot encode value with the selected converter"
                                         (.toPayload data-converter input-value))
      (encoding/with :something :null)
      (encoding/with :something "binary/null"))

    (are [input-value] (thrown-with-msg? DataConverterException
                                         #"No PayloadConverter is registered for this encoding"
                                         (.toPayload data-converter input-value))
      (encoding/with nil :nippy)
      (encoding/with nil "binary/plain")

      (encoding/with nil :json)
      (encoding/with nil "json/plain"))))

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
    (->payload-json "\"Raw JSON string\"")))
