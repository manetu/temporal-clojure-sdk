(defproject temporal.sample.signals-queries "0.1.0-SNAPSHOT"
  :description "Demonstrates signals and queries for workflow communication"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.github.manetu/temporal-sdk "1.6.0-SNAPSHOT"]
                 [environ "1.2.0"]]
  :main ^:skip-aot temporal.sample.signals-queries.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
