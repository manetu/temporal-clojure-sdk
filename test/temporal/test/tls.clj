;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.tls
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [temporal.tls :as tls]))

;;-----------------------------------------------------------------------------
;; Tests
;;-----------------------------------------------------------------------------

(deftest positive-tests
  (testing "Verify that we can create a default SSL context"
    (let [ctx (tls/new-ssl-context {})]
      (is (some? ctx))))
  (testing "Verify that we can create an SSL context with only a CA"
    (let [ctx (tls/new-ssl-context {:ca-path (io/resource "ca.crt")})]
      (is (some? ctx))))
  (testing "Verify that we can create an SSL context with only mTLS cert"
    (let [ctx (tls/new-ssl-context {:cert-path (io/resource "tls.crt") :key-path (io/resource "tls.key")})]
      (is (some? ctx)))))

(deftest negative-tests
  (testing "Verify that we cannot create an SSL context with a bogus CA"
    (is (thrown? java.io.FileNotFoundException (tls/new-ssl-context {:ca-path "invalid"}))))
  (testing "Verify that we cannot create an SSL context with a bogus Cert"
    (is (thrown? java.lang.IllegalArgumentException (tls/new-ssl-context {:cert-path "invalid" :key-path "invalid"})))))
