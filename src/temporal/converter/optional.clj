(ns temporal.converter.optional
  (:refer-clojure :exclude [empty])
  (:import
   [java.util Optional]))

(def empty
  (Optional/empty))

(defn optional? [value]
  (instance? Optional value))

;; NOTE: do we need this?
(defn present? [value]
  (and (optional? value) (.isPresent ^Optional value)))

(defn ->optional [value]
  (if value
    (Optional/of value)
    empty))
