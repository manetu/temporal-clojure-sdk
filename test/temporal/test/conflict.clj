;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.conflict
  (:require [clojure.test :refer :all]
            [temporal.internal.utils :as u]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(deftest conflict-detect-test
  (testing "Verify that we detect symbol conflicts"
    (is (thrown? clojure.lang.ExceptionInfo
                 (u/verify-registered-fns [{:name "foo" :fqn "bar.foo"} {:name "foo" :fqn "baz.foo"}])))))
