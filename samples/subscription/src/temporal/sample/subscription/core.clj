(ns temporal.sample.subscription.core
  "Demonstrates long-running workflows with continue-as-new.

  This sample shows:
  - Building a subscription/polling workflow
  - Using sleep for periodic execution
  - Using continue-as-new to prevent unbounded history growth
  - Handling cancellation signals for graceful shutdown"
  (:require [taoensso.timbre :as log]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.signals :as s])
  (:import [java.time Duration]))

;; Configuration
(def poll-interval (Duration/ofSeconds 2))  ;; How often to check for updates
(def iterations-before-continue 5)          ;; Continue-as-new after this many iterations

;; -----------------------------------------------------------------------------
;; Activities
;; -----------------------------------------------------------------------------

(defactivity fetch-updates
  "Fetches updates from an external source (simulated).
  In a real application, this might poll an API, check a database, etc."
  [_ctx {:keys [subscription-id last-check-time]}]
  (log/info "Checking for updates for subscription:" subscription-id
            "since:" last-check-time)
  ;; Simulate API call
  (Thread/sleep 100)
  ;; Return some fake updates
  (let [now (System/currentTimeMillis)
        has-updates? (< (rand) 0.4)]  ;; 40% chance of updates
    (if has-updates?
      {:updates [{:id (rand-int 1000)
                  :message (str "Update at " now)}]
       :checked-at now}
      {:updates []
       :checked-at now})))

(defactivity process-update
  "Processes a single update."
  [_ctx {:keys [subscription-id update]}]
  (log/info "Processing update for" subscription-id ":" update)
  (Thread/sleep 50)
  {:processed true
   :update-id (:id update)})

(defactivity send-notification
  "Sends a notification about processed updates."
  [_ctx {:keys [subscription-id update-count]}]
  (log/info "Sending notification:" update-count "updates processed for" subscription-id)
  (Thread/sleep 50)
  :notified)

;; -----------------------------------------------------------------------------
;; Workflow
;; -----------------------------------------------------------------------------

(def activity-opts {:start-to-close-timeout (Duration/ofSeconds 10)})

(defworkflow subscription-workflow
  "A long-running workflow that periodically checks for updates.

  This workflow demonstrates the subscription/polling pattern:
  1. Sleep for an interval
  2. Check for updates
  3. Process any updates found
  4. Repeat

  To prevent unbounded history growth, it uses continue-as-new after
  a configured number of iterations, passing state to the new run."
  [{:keys [subscription-id iteration total-processed last-check-time]}]
  (let [iteration (or iteration 0)
        total-processed (or total-processed 0)
        last-check-time (or last-check-time 0)]

    (log/info "Subscription workflow iteration" iteration
              "for" subscription-id
              "- total processed:" total-processed)

    ;; Create signal channel for cancellation
    (let [signals (s/create-signal-chan)]

      ;; Check if we should continue-as-new to prevent history growth
      (if (>= iteration iterations-before-continue)
        (do
          (log/info "Reached" iterations-before-continue
                    "iterations, continuing as new workflow...")
          (w/continue-as-new {:subscription-id subscription-id
                              :iteration 0
                              :total-processed total-processed
                              :last-check-time last-check-time}))

        ;; Normal iteration
        (do
          ;; Sleep for the poll interval, but check for cancel signal
          (log/info "Sleeping for" (.toSeconds poll-interval) "seconds...")
          (let [cancelled? (s/<! signals ::cancel poll-interval)]
            (if cancelled?
              ;; Graceful shutdown
              (do
                (log/info "Received cancel signal, shutting down gracefully...")
                {:status :cancelled
                 :subscription-id subscription-id
                 :total-iterations iteration
                 :total-processed total-processed})

              ;; Fetch and process updates
              (let [fetch-result @(a/invoke fetch-updates
                                            {:subscription-id subscription-id
                                             :last-check-time last-check-time}
                                            activity-opts)
                    updates (:updates fetch-result)
                    new-last-check (:checked-at fetch-result)]

                ;; Process each update
                (when (seq updates)
                  (log/info "Found" (count updates) "updates to process")
                  (doseq [update updates]
                    @(a/invoke process-update
                               {:subscription-id subscription-id
                                :update update}
                               activity-opts))

                  ;; Send notification
                  @(a/invoke send-notification
                             {:subscription-id subscription-id
                              :update-count (count updates)}
                             activity-opts))

                ;; Recurse for next iteration
                (let [processed-this-round (count updates)]
                  (w/continue-as-new {:subscription-id subscription-id
                                      :iteration (inc iteration)
                                      :total-processed (+ total-processed processed-this-round)
                                      :last-check-time new-last-check}))))))))))
