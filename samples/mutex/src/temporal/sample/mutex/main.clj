(ns temporal.sample.mutex.main
  (:require [taoensso.timbre :as log]
            [temporal.sample.mutex.core :as core])
  (:gen-class))

(log/set-level! :info)

(defn -main
  [& args]
  (core/init))
