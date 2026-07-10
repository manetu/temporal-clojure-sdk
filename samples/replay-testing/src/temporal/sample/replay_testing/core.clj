(ns temporal.sample.replay-testing.core
  "A simple order-processing workflow demonstrating what replay testing protects.

  The workflow calls two activities in sequence — validate-order then charge-payment.
  Any code change that reorders, adds, or removes these steps breaks replay, which
  the temporal.testing.replayer namespace detects in CI before the change reaches
  production workers."
  (:require [taoensso.timbre :as log]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.workflow :refer [defworkflow]])
  (:import [java.time Duration]))

(def ^:private activity-opts {:start-to-close-timeout (Duration/ofSeconds 30)
                              :retry-options {:maximum-attempts 1}})

;; ---------------------------------------------------------------------------
;; Activities
;; ---------------------------------------------------------------------------

(defactivity validate-order
  [_ctx {:keys [order-id]}]
  (log/info "Validating order:" order-id)
  {:valid? true :order-id order-id})

(defactivity charge-payment
  [_ctx {:keys [order-id amount]}]
  (log/info "Charging payment for order:" order-id "amount:" amount)
  {:charged? true :order-id order-id :amount amount})

;; ---------------------------------------------------------------------------
;; Workflow — v1 implementation (validate → charge)
;; ---------------------------------------------------------------------------

;; `workflow-version` is an atom used in tests to simulate a code change
;; between workflow versions without redeploying.  In a real project this
;; would simply be a change to the function body checked in to version control.
(def workflow-version (atom :v1))

(defworkflow order-workflow
  [{:keys [order-id]}]
  (log/info "order-workflow starting for" order-id "using" @workflow-version)
  (if (= :v1 @workflow-version)
    ;; v1: validate first, then charge
    (do
      @(a/invoke validate-order {:order-id order-id} activity-opts)
      @(a/invoke charge-payment {:order-id order-id :amount 100} activity-opts)
      {:status :ok :order-id order-id})
    ;; v2: charge first — WRONG ORDER, breaks replay of any v1 history
    (do
      @(a/invoke charge-payment {:order-id order-id :amount 100} activity-opts)
      @(a/invoke validate-order {:order-id order-id} activity-opts)
      {:status :ok :order-id order-id})))
