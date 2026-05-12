;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.codec
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.codec :refer [Codec] :as codec]))

(deftype NopCodec []
  Codec
  (encode [_ payload]
    (log/debug "encode:" payload)
    (assoc-in payload [:metadata "nop"] (byte-array [1])))
  (decode [_ payload]
    (log/debug "decode:" payload)
    (update payload :metadata dissoc "nop")))

(deftest the-test
  (testing "verify that we can create a functional DataConverter"
    (let [bytes-type (Class/forName "[B")
          c (codec/create (NopCodec.))
          d (byte-array [1 2 3 4])
          r (as-> d $
              (.toPayload c $)
              (.get $)
              (.fromPayload c $ bytes-type bytes-type))]

      (is (= (into [] d) (into [] r))))))
