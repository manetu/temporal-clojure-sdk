;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.legacy-flags-test
  "Temporal Java SDK 1.40 turned on the CANCEL_AWAIT_TIMER_ON_CONDITION and VERSION_WAIT_FOR_MARKER
  SDK flags by default (#3099).  These tests verify that histories recorded by an older SDK, before the
  flags existed, still replay, and that the workflow shapes affected by the flags behave under the new defaults."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [temporal.client.core :refer [>!] :as c]
            [temporal.signals :as s]
            [temporal.testing.history :as history]
            [temporal.testing.replayer :as replayer]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow] :as w])
  (:import [java.time Duration]))

(use-fixtures :once t/wrap-service)

(def signal-name ::poke)

;; Exercises both flagged code paths: get-version (VERSION_WAIT_FOR_MARKER) and a timed await
;; satisfied by a signal before the timeout fires (CANCEL_AWAIT_TIMER_ON_CONDITION).
;; NOTE: resources/histories/legacy-flags.json was recorded from this workflow under SDK 1.38.0;
;; changing its command sequence invalidates the fixture.
(defworkflow legacy-flags-workflow
  [_]
  (let [state   (atom 0)
        version (w/get-version ::legacy w/default-version 1)]
    (s/register-signal-handler! (fn [_ _] (swap! state inc)))
    (let [satisfied? (w/await (Duration/ofMinutes 5) (fn [] (pos? @state)))]
      {:version version :satisfied? satisfied?})))

(deftest replay-pre-1_40-history-test
  (testing "a history recorded under SDK 1.38 (flags off) replays under the 1.40 flag defaults"
    (let [h (history/from-resource "histories/legacy-flags.json")]
      (is (nil? (replayer/replay-history h))))))

(deftest await-and-version-under-new-defaults-test
  (testing "timed await satisfied by a signal completes, and its own history replays"
    (let [client (t/get-client)
          wf-id  (str "legacy-flags-" (random-uuid))
          wf     (c/create-workflow client legacy-flags-workflow
                                    {:task-queue t/task-queue :workflow-id wf-id})]
      (c/start wf {})
      (>! wf signal-name {})
      (is (= {:version 1 :satisfied? true} @(c/get-result wf)))
      (is (nil? (replayer/replay-history (history/fetch client wf-id)))))))
