# Saga Sample

Demonstrates the Saga pattern for distributed transactions with compensation.

## What This Sample Shows

- Implementing a saga with multiple steps
- Tracking compensating actions for rollback
- Handling failures and executing compensations in reverse order
- Using slingshot for structured error handling

## The Saga Pattern

A saga is a sequence of local transactions where each step has a compensating action. If any step fails, the saga executes compensations for all completed steps in reverse order.

```
Step 1: Book Flight  ──→  Compensation: Cancel Flight
        ↓
Step 2: Book Hotel   ──→  Compensation: Cancel Hotel
        ↓
Step 3: Book Car     ──→  Compensation: Cancel Car
        ↓
      Success!

If Step 3 fails:
  - Cancel Hotel (compensation for Step 2)
  - Cancel Flight (compensation for Step 1)
```

## Prerequisites

A running Temporal server:

```bash
temporal server start-dev
```

## Running the Sample

```bash
cd samples/saga
lein run
```

## Expected Output

### Successful Booking

```
Example 1: Successful Trip Booking
Step 1: Booking flight...
Booking flight to Paris for trip TRIP-001
Flight booked: FL-1234
Step 2: Booking hotel...
Booking hotel in Paris for 5 nights
Hotel booked: HT-5678
Step 3: Booking car...
Booking car in Paris
Car booked: CR-9012
All bookings completed successfully!
Trip booking result: {:status :success, :trip-id "TRIP-001", :bookings {...}}
```

### Failed Booking with Compensation

```
Example 2: Failed Booking with Compensation
Step 1: Booking flight...
Flight booked: FL-3456
Step 2: Booking hotel...
Hotel booked: HT-7890
Step 3: Booking car...
Car booking failed! No cars available.
Booking failed for :car - No cars available
Initiating saga compensation...
Running 2 compensations...
Cancelling hotel: HT-7890
Hotel cancelled: HT-7890
Cancelling flight: FL-3456
Flight cancelled: FL-3456
Trip booking result: {:status :failed, :trip-id "TRIP-002", :failed-step :car, ...}
```

## Key Concepts

### Tracking Compensations

As each step succeeds, register its compensation:

```clojure
(let [compensations (atom [])]
  ;; After successful booking
  (swap! compensations conj {:activity cancel-flight
                             :args {:confirmation confirmation}})
  ...)
```

### Running Compensations

On failure, execute compensations in reverse order:

```clojure
(doseq [{:keys [activity args]} (reverse @compensations)]
  @(a/invoke activity args opts))
```

### Error Handling with Slingshot

Use structured exceptions for saga failures:

```clojure
(throw+ {:type ::booking-failed
         :service :car
         :reason "No cars available"})

(try+
  ...
  (catch [:type ::booking-failed] {:keys [service reason]}
    (run-compensations @compensations)))
```

## Use Cases

- **Travel booking**: Flight + Hotel + Car rentals
- **Order fulfillment**: Reserve inventory → Charge payment → Ship order
- **Account creation**: Create user → Setup billing → Provision resources
- **Financial transfers**: Debit source → Credit destination
