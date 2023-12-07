;; Copyright © Manetu, Inc.  All rights reserved

(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh]]
            [eftest.runner :refer [find-tests run-tests]]))

;; to run one test: `(run-tests (find-tests #'temporal.test.simple/the-test))`
;; to see output, use (run-tests ... {:capture-output? false})

(defn run-all-tests []
  (run-tests (find-tests "test") {:fail-fast? true :multithread? false}))


