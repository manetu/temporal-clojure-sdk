;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.core
  (:import [io.temporal.workflow Workflow]
           [java.util.function Supplier]))

(defn- ->supplier
  [f]
  (reify Supplier
    (get [_]
      (f))))

(defn await
  "Efficiently parks the workflow until 'pred' evaluates to true.  Re-evaluates on each state transition"
  ([pred]
   (Workflow/await (->supplier pred)))
  ([duration pred]
   (Workflow/await duration (->supplier pred))))

(defn gen-uuid
  []
  (str (Workflow/randomUUID)))