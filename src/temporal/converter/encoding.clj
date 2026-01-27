(ns temporal.converter.encoding)

(defrecord Tagged [value encoding])

(def mapping
  {:null "binary/null"
   :nippy "binary/plain"
   :json "json/plain"})

(defn tagged? [x]
  (instance? Tagged x))

(defn with [value encoding]
  (->Tagged value (get mapping encoding encoding)))
