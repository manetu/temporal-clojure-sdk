(defproject temporal.sample.batch-processor "0.1.0-SNAPSHOT"
  :description "Demonstrates child workflows and parallel processing patterns"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.github.manetu/temporal-sdk "1.6.0-SNAPSHOT"]
                 [environ "1.2.0"]]
  :main ^:skip-aot temporal.sample.batch-processor.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
