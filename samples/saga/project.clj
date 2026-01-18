(defproject temporal.sample.saga "0.1.0-SNAPSHOT"
  :description "Demonstrates the Saga pattern for distributed transactions with compensation"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.github.manetu/temporal-sdk "1.6.0-SNAPSHOT"]
                 [slingshot "0.12.2"]
                 [environ "1.2.0"]]
  :main ^:skip-aot temporal.sample.saga.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
