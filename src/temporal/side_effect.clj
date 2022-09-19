;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.side-effect
  "Methods for managing side-effects from within workflows"
  (:import [io.temporal.workflow Workflow]))

(defn gen-uuid
  "A side-effect friendly random UUID generator"
  []
  (str (Workflow/randomUUID)))
