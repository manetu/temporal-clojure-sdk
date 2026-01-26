(ns temporal.client.options-test
  (:require
   [clojure.test :refer [are deftest]]
   [temporal.client.options :as sut])
  (:import
   [io.temporal.common.converter DataConverter DefaultDataConverter]))

(def MockDataConverter
  (reify DataConverter))

(deftest workflow-client-options->without-data-converter
  (are [data-converter-class options] (->> options
                                           (sut/workflow-client-options->)
                                           (.getDataConverter)
                                           (instance? data-converter-class))
    DefaultDataConverter {}
    DefaultDataConverter nil
    DataConverter {:data-converter MockDataConverter}))

(deftest schedule-client-options->without-data-converter
  (are [data-converter-class options] (->> options
                                           (sut/schedule-client-options->)
                                           (.getDataConverter)
                                           (instance? data-converter-class))
    DefaultDataConverter {}
    DefaultDataConverter nil
    DataConverter {:data-converter MockDataConverter}))
