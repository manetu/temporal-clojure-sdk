(defproject temporal.sample.mutex "0.1.0-SNAPSHOT"
  :description "Demonstrates the ability to lock/unlock a particular resource within a particular Temporal Namespace."
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.github.manetu/temporal-sdk "0.9.2"]
                 [environ "1.2.0"]]
  :main ^:skip-aot temporal.sample.mutex.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
