;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.utils
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [taoensso.timbre :as log]
            [promesa.core :as p])
  (:import [io.temporal.common.converter EncodedValues]
           [io.temporal.workflow Promise
            Functions$Func
            Functions$Func1
            Functions$Func2
            Functions$Func3
            Functions$Func4
            Functions$Func5
            Functions$Func6]))

(defn build [builder spec params]
  (doseq [[key value] params]
    (log/trace "building" builder "->" key "=" value)
    ((get spec key) builder value))
  (.build builder))

(defn get-annotation
  "Retrieves metadata annotation 'a' from 'v'"
  ^String [v a]
  (-> v meta a))

(def find-annotated-fns
  "Finds all instances of functions annotated with 'marker' via metadata and returns a [name fn] map"
  (memoize
   (fn [marker]
     (->> (all-ns)
          (mapcat (comp vals ns-interns ns-name))
          (reduce (fn [acc x]
                    (let [v (var-get x)
                          m (get-annotation v marker)]
                      (cond-> acc
                        (some? m) (assoc m v)))) {})))))

(defn find-annotated-fn
  "Finds any functions named 't' that carry metadata 'marker'"
  [marker t]
  (get (find-annotated-fns marker) t))

(defn get-classname
  "Returns the fully qualified classname for 'sym'"
  [sym]
  (-> (ns-name *ns*)
      (clojure.core/name)
      (string/replace #"-" "_")
      (str "." sym)))

(defn ->objarray
  "Converts a collection of serializable elements to an array of Objects, suitable for many Temporal APIs"
  [coll]
  (into-array Object [(prn-str coll)]))

(defn ->args
  "Decodes EncodedValues to native clojure data type.  Assumes all data is in the first element"
  [^EncodedValues args]
  (edn/read-string (.get args (int 0) ^Class String)))

(defn wrap-encoded
  "Wraps 'f' in a codec, EncodedValues args in, and edn out"
  [f args]
  (-> args
      (->args)
      (f)
      (prn-str)))

(def namify
  "Converts strings or keywords to strings, preserving fully qualified keywords when applicable"
  (memoize
   (fn [x]
     (str (symbol x)))))

(defn ->Func
  [f]
  (reify
    Functions$Func
    (apply [_]
      (f))
    Functions$Func1
    (apply [_ x1]
      (f x1))
    Functions$Func2
    (apply [_ x1 x2]
      (f x1 x2))
    Functions$Func3
    (apply [_ x1 x2 x3]
      (f x1 x2 x3))
    Functions$Func4
    (apply [_ x1 x2 x3 x4]
      (f x1 x2 x3 x4))
    Functions$Func5
    (apply [_ x1 x2 x3 x4 x5]
      (f x1 x2 x3 x4 x5))
    Functions$Func6
    (apply [_ x1 x2 x3 x4 x5 x6]
      (f x1 x2 x3 x4 x5 x6))))

(defn ->promise
  [^Promise p]
  (p/create
   (fn [resolve reject]
     (resolve (.get p)))))
