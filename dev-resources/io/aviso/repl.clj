;; Copyright © Manetu, Inc.  All rights reserved

(ns io.aviso.repl
  "Dev-only shim.  timbre >= 6.7 depends on org.clj-commons/pretty, whose own io.aviso.repl
  compatibility shim lacks `pretty-print-stack-trace`, which eftest 0.6.0 requires.  This
  namespace takes precedence on the classpath (dev-resources) and supplies the missing fn."
  (:require [clj-commons.format.exceptions :as e]
            [clj-commons.pretty.repl :as repl]))

(defn install-pretty-exceptions []
  (repl/install-pretty-exceptions))

(defn uncaught-exception-handler []
  (repl/uncaught-exception-handler))

(defn pretty-print-stack-trace
  ([exception]
   (e/print-exception exception))
  ([exception _depth]
   (e/print-exception exception)))
