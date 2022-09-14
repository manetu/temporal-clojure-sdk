# Testing Your Workflows

## Overview

This Temporal Clojure SDK provides a test framework to facilitate Workflow unit-testing. The test framework includes an in-memory implementation of the Temporal service.

You can use the provided environment with a Clojure unit testing framework of your choice, such as `clojure.test`.

## Example

```clojure
(ns my.tests
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]))

(def task-queue "MyTaskQueue")

(defactivity greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow greeter-workflow
  [ctx {:keys [args]}]
  (log/info "greeter-workflow:" args)
  @(a/invoke greet-activity args))

(deftest my-test
  (testing "Verifies that we can invoke our greeter workflow"
    (let [{:keys [client] :as env} (e/create {:task-queue task-queue})
          (c/create-workflow client greeter-workflow {:task-queue task-queue})]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))
```

The primary difference between this test and a real-world application is the use of ([temporal.testing.env/create](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.env#create)) function.  This method simultaneously creates an instance of the in-memory Temporal service *and* a client already connected to this environment.

Typical tests may opt to create the testing environment within a [fixture](https://clojuredocs.org/clojure.test/use-fixtures), but this is left as an exercise to the reader.  The testing environment may be cleanly shutdown with ([stop](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.testing.env#stop))