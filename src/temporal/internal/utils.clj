;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.utils
  (:require [clojure.string :as string]
            [medley.core :as m]
            [taoensso.timbre :as log])
  (:import [io.temporal.common.converter EncodedValues]
           [io.temporal.workflow Functions$Func
            Functions$Func1
            Functions$Func2
            Functions$Func3
            Functions$Func4
            Functions$Func5
            Functions$Func6]))

(def ^Class object-type Object)

(defn build [builder spec params]
  (log/trace "building" builder "with" params)
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
  ([marker]
   (find-annotated-fns marker {}))
  ([marker {:keys [hot-reload?] :or {hot-reload? false}}]
   (->> (all-ns)
        (mapcat (comp vals ns-interns ns-name))
        (reduce (fn [acc x]
                  (let [v (var-get x)
                        m (get-annotation v marker)]
                    (cond-> acc
                      (some? m) (conj (assoc m (if hot-reload? :var :fn) (if hot-reload? x v)))))) []))))

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
  ([marker]
   (get-annotated-fns marker {}))
  ([marker opts]
   (let [data (find-annotated-fns marker opts)]
     (verify-registered-fns data)
     (m/index-by :name data))))

(defn find-dispatch
  "Finds any dispatch descriptor named 't' that carry metadata 'marker'"
  [dispatch-table t]
  (or (get dispatch-table t)
      (throw (ex-info "workflow/activity not found" {:function t}))))

(defn resolve-dispatch-fn
  "Resolves a dispatch entry to a function value. If resolve-dispatch already
  cached a function in the entry, use that value; otherwise dereference the Var
  at execution time so re-evaluated definitions are picked up."
  [entry]
  (if (contains? entry :fn)
    (:fn entry)
    (some-> entry :var deref)))

(defn resolve-dispatch
  "Resolves a dispatch entry, refreshing marker metadata from the current Var value
  when possible. This keeps workflow metadata such as :type in sync during hot reload."
  [marker entry]
  (if-let [v (:var entry)]
    (let [f @v]
      (if-let [m (get-annotation f marker)]
        (assoc m :var v :fn f)
        (assoc entry :fn f)))
    entry))

(defn- dispatch-entry
  [marker x hot-reload?]
  (let [f (if (var? x) @x x)
        {:keys [name] :as m} (get-annotation f marker)
        v (or (when (var? x) x) (::var (meta f)))]
    (when-not name
      (throw (ex-info "bad symbol" {:message (str x " does not appear to be a valid symbol")})))
    (if (and hot-reload? v)
      (assoc m :var v)
      (assoc m :fn f))))

(defn import-dispatch
  ([marker coll]
   (import-dispatch marker coll {}))
  ([marker coll {:keys [hot-reload?] :or {hot-reload? false}}]
   (reduce (fn [acc s]
             (let [{:keys [name] :as x} (dispatch-entry marker s hot-reload?)]
               (assoc acc name x)))
           {}
           coll)))

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
  (into-array object-type [x]))

(defn ->args
  "Decodes EncodedValues to native clojure data type.  Assumes all data is in the first element"
  [^EncodedValues args]
  (log/trace "args:" args)
  (.get args (int 0) object-type))

(def namify
  "Converts strings or keywords to strings, preserving fully qualified keywords when applicable"
  (memoize
   (fn [x]
     (str (symbol x)))))

(defn complete-invoke
  [stub result]
  (log/trace stub "completed with" result)

  result)

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
