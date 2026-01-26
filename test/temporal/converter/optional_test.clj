(ns temporal.converter.optional-test
  (:require
   [clojure.template :refer [do-template]]
   [clojure.test :refer [are deftest is testing]]
   [temporal.converter.optional :as sut])
  (:import
   [java.util Optional]))

(deftest ->optional-with-present-values
  (do-template
   [value]

   (testing (str "encoding: " (class value))
     (let [optional (sut/->optional value)]
       (is (instance? Optional optional))
       (is (.isPresent optional))
       (is (= value (.get optional)))))

   "Something"
   :foo
   [:a :b :c]))

(deftest ->optional-with-nil
  (let [optional (sut/->optional nil)]
    (is (instance? Optional optional))
    (is (not (.isPresent optional)))))

(deftest present?
  (are [value] (sut/present? value)
    (Optional/of "test")
    (Optional/of :something)
    (Optional/of [:a :b :c])
    (sut/->optional "test")
    (sut/->optional :something)
    (sut/->optional [:a :b :c]))

  (are [value] (not (sut/present? value))
    (Optional/empty)
    (sut/->optional nil)))
