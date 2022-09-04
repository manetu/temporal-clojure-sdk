;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.signals
  "Methods for managing signals from within workflows"
  (:require [taoensso.timbre :as log]
            [temporal.core :as core]
            [temporal.internal.utils :as u]
            [temporal.internal.signals :as s]))

(defn is-empty?
  "Returns 'true' if 'signal-name' either doesn't exist or exists but has no pending messages"
  [state signal-name]
  (let [ch (s/get-ch @state signal-name)]
    (or (nil? ch) (not (.isEmpty ch)))))

(defn <!
  "Light-weight/parking receive of a single message"
  ([state] (<! state ::default))
  ([state signal-name]
   (let [signal-name (u/namify signal-name)]
     (log/trace "waiting on:" signal-name)
     (core/await (partial is-empty? state signal-name))
     (let [ch (s/get-ch @state signal-name)
           m (.poll ch)]
       (log/trace "rx:" m)
       m))))
