(ns temporal.converter.optional-test
  (:require
   [clojure.template :refer [do-template]]
   [clojure.test :refer [deftest is testing]]
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
  (is (thrown? NullPointerException (sut/->optional nil))))

(deftest ->optional-with-false
  (let [optional (sut/->optional false)]
    (is (instance? Optional optional))
    (is (.isPresent optional))
    (is (= false (.get optional)))))
