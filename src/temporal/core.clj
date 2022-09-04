;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.core
  (:import [io.temporal.workflow Workflow]
           [java.util.function Supplier]))

(defn await
  "Efficiently parks the workflow until 'pred' evaluates to true.  Re-evaluates on each state transition"
  [pred]
  (Workflow/await
   (reify Supplier
     (get [_]
       (pred)))))
