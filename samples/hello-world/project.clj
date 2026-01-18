(defproject temporal.sample.hello-world "0.1.0-SNAPSHOT"
  :description "A simple Hello World example demonstrating basic Temporal workflow and activity usage"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.github.manetu/temporal-sdk "1.6.0-SNAPSHOT"]
                 [environ "1.2.0"]]
  :main ^:skip-aot temporal.sample.hello-world.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
