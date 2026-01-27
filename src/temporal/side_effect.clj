;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.side-effect
  "Methods for managing side-effects from within workflows"
  (:require [temporal.internal.utils :refer [->Func] :as u])
  (:import [io.temporal.workflow Workflow]
           [java.time Instant]))

(defn gen-uuid
  "A side-effect friendly random UUID generator"
  []
  (str (Workflow/randomUUID)))

(defn invoke
  "Invokes 'f' via a Temporal [SideEffect](https://docs.temporal.io/concepts/what-is-a-side-effect/)"
  [f]
  (Workflow/sideEffect u/bytes-type (->Func f)))

(defn now
  "Returns the java.time.Instant as a SideEffect"
  []
  (invoke #(Instant/now)))
