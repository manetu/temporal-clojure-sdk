(ns temporal.test.hot-reload
  (:require [clojure.test :refer [deftest is]]
            [criterium.core :as criterium]
            [temporal.activity :refer [defactivity]]
            [temporal.internal.activity :as ia]
            [temporal.internal.utils :as u]
            [temporal.internal.workflow :as iw]
            [temporal.workflow :refer [defworkflow]]))

(defn- report-benchmark
  [label data]
  (println (str "\n" label))
  (doseq [[k v] data]
    (println " " (name k) "=" v)))

(defn- criterium-ms
  [f]
  (let [result (criterium/quick-benchmark* f {})]
    (* 1000.0 (first (:mean result)))))

(defn- format-ms
  [x]
  (format "%.9f" (double x)))

(defn- format-speedup
  [x]
  (if (number? x)
    (format "%.3fx" (double x))
    x))

(defn- report-criterium-comparison
  [label benches]
  (report-benchmark
   label
   (into {}
         (map (fn [[k f]] [k (format-ms (criterium-ms f))]))
         benches)))

(defn- report-criterium-dispatch-loading-comparison
  [label {:keys [static-dispatch hot-reload-dispatch]}]
  (let [static-ms (criterium-ms static-dispatch)
        hot-ms (criterium-ms hot-reload-dispatch)]
    (report-benchmark
     label
     {:static-dispatch-ms (format-ms static-ms)
      :hot-reload-dispatch-ms (format-ms hot-ms)
      :difference-ms (format-ms (- hot-ms static-ms))
      :hot-reload-vs-static (format-speedup (if (pos? static-ms)
                                              (/ hot-ms static-ms)
                                              :infinite))})))

(defn- report-criterium-reload-comparison
  [label {:keys [old-static-reload hot-reload]}]
  (let [old-static-ms (criterium-ms old-static-reload)
        hot-ms (criterium-ms hot-reload)]
    (report-benchmark
     label
     {:old-static-reload-ms (format-ms old-static-ms)
      :hot-reload-ms (format-ms hot-ms)
      :saved-ms (format-ms (- old-static-ms hot-ms))
      :speedup (format-speedup (if (pos? hot-ms)
                                 (/ old-static-ms hot-ms)
                                 :infinite))})))

(defn- with-var-root
  [v new-root f]
  (let [old-root @v]
    (try
      (alter-var-root v (constantly new-root))
      (f)
      (finally
        (alter-var-root v (constantly old-root))))))

(defactivity hot-reload-activity
  [_ _]
  :original)

(defworkflow hot-reload-workflow
  [_]
  :original)

(deftest activity-hot-reload-dispatch-test
  (let [static-dispatch (ia/import-dispatch [hot-reload-activity])
        reload-dispatch (ia/import-dispatch [hot-reload-activity] {:hot-reload? true})
        static-entry (get static-dispatch "hot-reload-activity")
        reload-entry (get reload-dispatch "hot-reload-activity")]
    (is (fn? (:fn static-entry)))
    (is (var? (:var reload-entry)))
    (with-var-root #'hot-reload-activity
      (with-meta (fn [_ _] :reloaded) (meta hot-reload-activity))
      #(do
         (is (= :original ((u/resolve-dispatch-fn static-entry) nil nil)))
         (is (= :reloaded ((u/resolve-dispatch-fn reload-entry) nil nil)))))))

(deftest workflow-hot-reload-refreshes-dispatch-metadata-test
  (let [dispatch (iw/import-dispatch [hot-reload-workflow] {:hot-reload? true})
        entry (get dispatch "hot-reload-workflow")
        legacy-fn (with-meta
                    (fn [_ _] :legacy)
                    {::iw/def {:name "hot-reload-workflow"
                               :fqn "temporal.test.hot_reload.hot_reload_workflow"
                               :type :legacy}
                     ::u/var #'hot-reload-workflow})]
    (is (nil? (:type entry)))
    (with-var-root #'hot-reload-workflow
      legacy-fn
      #(is (= :legacy (:type (u/resolve-dispatch ::iw/def entry)))))
    (is (nil? (:type (u/resolve-dispatch ::iw/def (assoc entry :type :legacy)))))))

(deftest workflow-hot-reload-uses-one-var-snapshot-test
  (let [dispatch (iw/import-dispatch [hot-reload-workflow] {:hot-reload? true})
        entry (get dispatch "hot-reload-workflow")
        first-fn (with-meta (fn [_] :first) (meta hot-reload-workflow))
        second-fn (with-meta (fn [_] :second) (meta hot-reload-workflow))]
    (with-var-root #'hot-reload-workflow
      first-fn
      #(let [resolved-entry (u/resolve-dispatch ::iw/def entry)]
         (with-var-root #'hot-reload-workflow
           second-fn
           (fn []
             (is (= :first ((u/resolve-dispatch-fn resolved-entry) nil)))))))))

(defn- synthetic-activity-fns
  [n]
  (mapv (fn [i]
          (let [sym (symbol (str "synthetic-hot-reload-activity-" i))
                v (intern *ns* sym nil)
                f (with-meta
                    (fn [_ _] i)
                    {::ia/def {:name (str sym)
                               :fqn (str "temporal.test.hot_reload." sym)}
                     ::u/var v})]
            (alter-var-root v (constantly f))
            f))
        (range n)))

(defn- synthetic-workflow-fns
  [n]
  (mapv (fn [i]
          (let [sym (symbol (str "synthetic-hot-reload-workflow-" i))
                v (intern *ns* sym nil)
                f (with-meta
                    (fn [_] i)
                    {::iw/def {:name (str sym)
                               :fqn (str "temporal.test.hot_reload." sym)}
                     ::u/var v})]
            (alter-var-root v (constantly f))
            f))
        (range n)))

;; Criterium-backed benchmark helpers. These are intentionally plain functions,
;; not deftests, because Criterium benchmarks are slower and noisy in CI.
;; Run them manually from a REPL, e.g.:
;;
;;   (require 'temporal.test.hot-reload)
;;   (temporal.test.hot-reload/criterium-activity-reload-comparison)
;;   (temporal.test.hot-reload/criterium-workflow-reload-comparison)
;;   (temporal.test.hot-reload/criterium-activity-dispatch-loading-comparison 100000)
;;   (temporal.test.hot-reload/criterium-workflow-dispatch-loading-comparison 100000)

(defn criterium-activity-reload-comparison
  "Compares time-to-changed-activity-visible after a Var root change.

  Old static approach: rebuild the dispatch table, then call the changed activity.
  Hot-reload approach: reuse the existing hot-reload dispatch entry, deref its Var,
  then call the changed activity. This intentionally excludes full worker/system
  restart time, so old-static-reload-ms is a lower bound for the old approach."
  []
  (let [reload-dispatch (ia/import-dispatch [hot-reload-activity] {:hot-reload? true})
        reload-entry (get reload-dispatch "hot-reload-activity")
        reloaded-fn (with-meta (fn [_ _] :reloaded) (meta hot-reload-activity))
        old-static-reload #(let [d (ia/import-dispatch [hot-reload-activity])]
                             ((u/resolve-dispatch-fn (get d "hot-reload-activity")) nil nil))
        hot-reload #((u/resolve-dispatch-fn reload-entry) nil nil)]
    (with-var-root #'hot-reload-activity
      reloaded-fn
      (fn []
        (assert (= :reloaded (old-static-reload)))
        (assert (= :reloaded (hot-reload)))
        (report-criterium-reload-comparison
         "criterium activity reload comparison"
         {:old-static-reload old-static-reload
          :hot-reload hot-reload})))))

(defn criterium-workflow-reload-comparison
  "Compares time-to-changed-workflow-visible after a Var root change.

  Old static approach: rebuild the dispatch table, then call the changed workflow.
  Hot-reload approach: reuse the existing hot-reload dispatch entry, refresh current
  workflow metadata from its Var, then call the changed workflow. This intentionally
  excludes full worker/system restart time, so old-static-reload-ms is a lower bound
  for the old approach."
  []
  (let [reload-dispatch (iw/import-dispatch [hot-reload-workflow] {:hot-reload? true})
        reload-entry (get reload-dispatch "hot-reload-workflow")
        reloaded-fn (with-meta (fn [_] :reloaded) (meta hot-reload-workflow))
        old-static-reload #(let [d (iw/import-dispatch [hot-reload-workflow])]
                             ((u/resolve-dispatch-fn (get d "hot-reload-workflow")) nil))
        hot-reload #((u/resolve-dispatch-fn (u/resolve-dispatch ::iw/def reload-entry)) nil)]
    (with-var-root #'hot-reload-workflow
      reloaded-fn
      (fn []
        (assert (= :reloaded (old-static-reload)))
        (assert (= :reloaded (hot-reload)))
        (report-criterium-reload-comparison
         "criterium workflow reload comparison"
         {:old-static-reload old-static-reload
          :hot-reload hot-reload})))))

(defn criterium-activity-dispatch-loading-comparison
  ([]
   (criterium-activity-dispatch-loading-comparison 100000))
  ([n]
   (let [activities (synthetic-activity-fns n)]
     (report-criterium-dispatch-loading-comparison
      "criterium activity dispatch loading comparison"
      {:static-dispatch #(ia/import-dispatch activities)
       :hot-reload-dispatch #(ia/import-dispatch activities {:hot-reload? true})}))))

(defn criterium-workflow-dispatch-loading-comparison
  ([]
   (criterium-workflow-dispatch-loading-comparison 100000))
  ([n]
   (let [workflows (synthetic-workflow-fns n)]
     (report-criterium-dispatch-loading-comparison
      "criterium workflow dispatch loading comparison"
      {:static-dispatch #(iw/import-dispatch workflows)
       :hot-reload-dispatch #(iw/import-dispatch workflows {:hot-reload? true})}))))

(comment
  (criterium-activity-reload-comparison)
  (criterium-workflow-reload-comparison)
  (criterium-activity-dispatch-loading-comparison 100000)
  (criterium-workflow-dispatch-loading-comparison 100000))
