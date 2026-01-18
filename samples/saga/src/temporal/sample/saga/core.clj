(ns temporal.sample.saga.core
  "Demonstrates the Saga pattern for distributed transactions with compensation.

  This sample shows:
  - Implementing a saga with multiple steps
  - Tracking compensations for rollback
  - Handling failures and executing compensations in reverse order
  - Using slingshot for structured error handling"
  (:require [taoensso.timbre :as log]
            [slingshot.slingshot :refer [throw+ try+]]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a])
  (:import [java.time Duration]))

;; -----------------------------------------------------------------------------
;; Activities - Booking Actions
;; -----------------------------------------------------------------------------

(defactivity book-flight
  "Books a flight. Returns a confirmation number."
  [_ctx {:keys [trip-id destination]}]
  (log/info "Booking flight to" destination "for trip" trip-id)
  ;; Simulate API call
  (Thread/sleep 100)
  (let [confirmation (str "FL-" (rand-int 10000))]
    (log/info "Flight booked:" confirmation)
    {:confirmation confirmation
     :type :flight
     :destination destination}))

(defactivity book-hotel
  "Books a hotel. Returns a confirmation number."
  [_ctx {:keys [trip-id destination nights]}]
  (log/info "Booking hotel in" destination "for" nights "nights, trip" trip-id)
  ;; Simulate API call
  (Thread/sleep 100)
  (let [confirmation (str "HT-" (rand-int 10000))]
    (log/info "Hotel booked:" confirmation)
    {:confirmation confirmation
     :type :hotel
     :destination destination
     :nights nights}))

(defactivity book-car
  "Books a rental car. May fail to simulate a saga rollback."
  [_ctx {:keys [trip-id destination fail?]}]
  (log/info "Booking car in" destination "for trip" trip-id)
  ;; Simulate API call
  (Thread/sleep 100)
  (if fail?
    (do
      (log/error "Car booking failed! No cars available.")
      (throw+ {:type ::booking-failed
               :service :car
               :reason "No cars available"}))
    (let [confirmation (str "CR-" (rand-int 10000))]
      (log/info "Car booked:" confirmation)
      {:confirmation confirmation
       :type :car
       :destination destination})))

;; -----------------------------------------------------------------------------
;; Activities - Compensation Actions
;; -----------------------------------------------------------------------------

(defactivity cancel-flight
  "Cancels a flight booking."
  [_ctx {:keys [confirmation]}]
  (log/info "Cancelling flight:" confirmation)
  (Thread/sleep 50)
  (log/info "Flight cancelled:" confirmation)
  :cancelled)

(defactivity cancel-hotel
  "Cancels a hotel booking."
  [_ctx {:keys [confirmation]}]
  (log/info "Cancelling hotel:" confirmation)
  (Thread/sleep 50)
  (log/info "Hotel cancelled:" confirmation)
  :cancelled)

(defactivity cancel-car
  "Cancels a car rental booking."
  [_ctx {:keys [confirmation]}]
  (log/info "Cancelling car:" confirmation)
  (Thread/sleep 50)
  (log/info "Car cancelled:" confirmation)
  :cancelled)

;; -----------------------------------------------------------------------------
;; Workflow
;; -----------------------------------------------------------------------------

(def activity-opts
  {:start-to-close-timeout (Duration/ofSeconds 10)
   :retry-options {:maximum-attempts 1}})  ;; No retries for demo

(defn run-compensations
  "Runs compensation activities in reverse order."
  [compensations]
  (log/info "Running" (count compensations) "compensations...")
  (doseq [{:keys [activity args]} (reverse compensations)]
    (try+
     @(a/invoke activity args activity-opts)
     (catch Object e
       (log/error "Compensation failed (continuing anyway):" e)))))

(defworkflow trip-booking-saga
  "Books a complete trip (flight + hotel + car) using the Saga pattern.

  If any booking fails, all previous bookings are cancelled in reverse order.
  This ensures we don't leave partial bookings when something goes wrong."
  [{:keys [trip-id destination nights fail-car?]}]
  (log/info "Starting trip booking saga for trip:" trip-id)

  ;; Track compensations as we go
  (let [compensations (atom [])]

    (try+
      ;; Step 1: Book flight
     (log/info "Step 1: Booking flight...")
     (let [flight @(a/invoke book-flight
                             {:trip-id trip-id :destination destination}
                             activity-opts)]
        ;; Register compensation
       (swap! compensations conj {:activity cancel-flight
                                  :args {:confirmation (:confirmation flight)}})

        ;; Step 2: Book hotel
       (log/info "Step 2: Booking hotel...")
       (let [hotel @(a/invoke book-hotel
                              {:trip-id trip-id
                               :destination destination
                               :nights nights}
                              activity-opts)]
          ;; Register compensation
         (swap! compensations conj {:activity cancel-hotel
                                    :args {:confirmation (:confirmation hotel)}})

          ;; Step 3: Book car (may fail)
         (log/info "Step 3: Booking car...")
         (let [car @(a/invoke book-car
                              {:trip-id trip-id
                               :destination destination
                               :fail? fail-car?}
                              activity-opts)]

            ;; All bookings successful!
           (log/info "All bookings completed successfully!")
           {:status :success
            :trip-id trip-id
            :bookings {:flight flight
                       :hotel hotel
                       :car car}})))

     (catch [:type ::booking-failed] {:keys [service reason]}
       (log/error "Booking failed for" service "-" reason)
       (log/info "Initiating saga compensation...")

        ;; Run compensations in reverse order
       (run-compensations @compensations)

       {:status :failed
        :trip-id trip-id
        :failed-step service
        :reason reason
        :compensations-run (count @compensations)}))))
