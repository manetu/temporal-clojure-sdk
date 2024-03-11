;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.workflow
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [temporal.internal.utils :as u]
            [temporal.internal.signals :as s]
            [temporal.internal.exceptions :as e])
  (:import [io.temporal.workflow Workflow WorkflowInfo]))

(extend-protocol p/Datafiable
  WorkflowInfo
  (datafy [d]
    {:namespace     (.getNamespace d)
     :workflow-id   (.getWorkflowId d)
     :run-id        (.getRunId d)
     :workflow-type (.getWorkflowType d)
     :attempt       (.getAttempt d)}))

(defn get-info []
  (d/datafy (Workflow/getInfo)))

(defn get-annotated-name
  ^String [x]
  (u/get-annotated-name x ::def))

(defn auto-dispatch
  []
  (u/get-annotated-fns ::def))

(defn import-dispatch
  [syms]
  (u/import-dispatch ::def syms))

(defn execute
  [ctx dispatch args]
  (try+
   (let [{:keys [workflow-type workflow-id]} (get-info)
         d (u/find-dispatch dispatch workflow-type)
         f (:fn d)
         a (u/->args args)
         _ (log/trace workflow-id "calling" f "with args:" a)
         r (if (-> d :type (= :legacy))
             (f ctx {:args a :signals (s/create-signal-chan)})
             (f a))]
     (log/trace workflow-id "result:" r)
     (nippy/freeze r))
   (catch Object o
     (log/error &throw-context)
     (e/forward &throw-context))))
