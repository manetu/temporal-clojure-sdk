;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.utils
  (:require [clojure.string :as string]
            [medley.core :as m]
            [taoensso.timbre :as log]
            [taoensso.nippy :as nippy])
  (:import [io.temporal.common.converter EncodedValues]
           [io.temporal.workflow Functions$Func
            Functions$Func1
            Functions$Func2
            Functions$Func3
            Functions$Func4
            Functions$Func5
            Functions$Func6]))

(def ^Class bytes-type (Class/forName "[B"))

(defn build [builder spec params]
  (try
    (doseq [[key value] params]
      (if-let [f (get spec key)]
        (do
          (log/trace "building" builder "->" key "=" value)
          (f builder value))
        (log/trace "skipping" key)))
    (.build builder)
    (catch Exception e
      (log/error e))))

(defn get-annotation
  "Retrieves metadata annotation 'a' from 'v'"
  [v a]
  (-> v meta a))

(defn get-annotated-name
  ^String [v a]
  (let [x (get-annotation v a)]
    (or (:name x)
        (throw (ex-info "bad symbol" {:message (str v "does not appear to be a valid symbol")})))))

(defn find-annotated-fns
  "Finds all instances of functions annotated with 'marker' via metadata and returns a [name fn] map"
  [marker]
  (->> (all-ns)
       (mapcat (comp vals ns-interns ns-name))
       (reduce (fn [acc x]
                 (let [v (var-get x)
                       m (get-annotation v marker)]
                   (cond-> acc
                     (some? m) (conj (assoc m :fn v))))) [])))

(defn frequencies-by
  "a generalized version of frequencies"
  [f coll]
  (let [gp (group-by f coll)]
    (zipmap (keys gp)  (map #(count (second %)) gp))))

(defn verify-registered-fns
  [coll]
  (let [conflicts (->> (frequencies-by :name coll)
                       (m/filter-vals #(> % 1))
                       (keys)
                       (set))]
    (when-not (empty? conflicts)
      (let [data (->> (map (fn [{:keys [name fqn]}] [name fqn]) coll)
                      (group-by first)
                      (m/filter-keys conflicts)
                      (vals))]
        (throw (ex-info "conflict detected: All temporal workflows and activities must be uniquely named" {:conflicts data}))))))

(defn get-annotated-fns
  [marker]
  (let [data (find-annotated-fns marker)]
    (verify-registered-fns data)
    (m/index-by :name data)))

(defn find-dispatch
  "Finds any dispatch descriptor named 't' that carry metadata 'marker'"
  [dispatch-table t]
  (or (get dispatch-table t)
      (throw (ex-info "workflow/activity not found" {:function t}))))

(defn find-dispatch-fn
  "Finds any functions named 't' that carry metadata 'marker'"
  [dispatch-table t]
  (:fn (find-dispatch dispatch-table t)))

(defn import-dispatch
  [marker coll]
  (reduce (fn [acc s]
            (let [{:keys [name] :as x} (get-annotation s marker)]
              (assoc acc name (assoc x :fn s))))
          {}
          coll))

(defn get-fq-classname
  "Returns the fully qualified classname for 'sym'"
  [sym]
  (-> (ns-name *ns*)
      (clojure.core/name)
      (string/replace #"-" "_")
      (str "." sym)))

(defn ->objarray
  "Serializes x to an array of Objects, suitable for many Temporal APIs"
  [x]
  (into-array Object [(nippy/freeze x)]))

(defn ->args
  "Decodes EncodedValues to native clojure data type.  Assumes all data is in the first element"
  [^EncodedValues args]
  (nippy/thaw (.get args (int 0) bytes-type)))

(def namify
  "Converts strings or keywords to strings, preserving fully qualified keywords when applicable"
  (memoize
   (fn [x]
     (str (symbol x)))))

(defn complete-invoke
  [stub result]
  (log/trace stub "completed with" (count result) "bytes")
  (let [r (nippy/thaw result)]
    (log/trace stub "results:" r)
    r))

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
