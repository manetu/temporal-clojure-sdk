(ns temporal.converter.optional
  (:refer-clojure :exclude [empty])
  (:import
   [java.util Optional]))

(def empty
  (Optional/empty))

(defn optional? [value]
  (instance? Optional value))

(defn ->optional
  "Wraps a non-nil produced value in a present Optional.

     Use `empty` explicitly when no value was produced."
  [value]
  (Optional/of value))
