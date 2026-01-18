(ns temporal.sample.updates.core
  "Demonstrates workflow updates for synchronous request-response patterns.

  This sample shows:
  - Registering update handlers with register-update-handler!
  - Using validators to reject invalid updates
  - Sending synchronous updates with update
  - Using async updates with start-update
  - Combining updates with workflow start using update-with-start"
  (:require [taoensso.timbre :as log]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.signals :as s]))

;; -----------------------------------------------------------------------------
;; Workflow
;; -----------------------------------------------------------------------------

(defworkflow counter-workflow
  "A workflow that maintains a counter and accepts updates to modify it.

  Unlike signals, updates:
  - Return values to the caller
  - Can have validators that reject invalid requests
  - Support synchronous request-response patterns"
  [{:keys [initial-value]}]
  (log/info "Starting counter workflow with initial value:" initial-value)

  (let [state (atom {:counter (or initial-value 0)
                     :history []})
        signals (s/create-signal-chan)]

    ;; Register an update handler with a validator
    (w/register-update-handler!
     (fn [update-type args]
       (log/info "Processing update:" update-type args)
       (case update-type
         :increment
         (let [{:keys [amount]} args]
           (swap! state (fn [s]
                          (-> s
                              (update :counter + amount)
                              (update :history conj {:op :increment :amount amount}))))
           ;; Return the new counter value
           (:counter @state))

         :decrement
         (let [{:keys [amount]} args]
           (swap! state (fn [s]
                          (-> s
                              (update :counter - amount)
                              (update :history conj {:op :decrement :amount amount}))))
           (:counter @state))

         :get-value
         (:counter @state)

         :get-history
         (:history @state)

         ;; Unknown update type
         (throw (ex-info "Unknown update type" {:type update-type}))))

     ;; Validator - runs before the handler
     ;; Throw an exception to reject the update
     {:validator
      (fn [update-type args]
        (log/info "Validating update:" update-type args)
        (when (#{:increment :decrement} update-type)
          (let [{:keys [amount]} args]
            (when-not (and (number? amount) (pos? amount))
              (throw (IllegalArgumentException.
                      "amount must be a positive number"))))))})

    ;; Wait for a done signal to complete the workflow
    (s/<! signals ::done)

    (log/info "Counter workflow completed. Final state:" @state)
    @state))
