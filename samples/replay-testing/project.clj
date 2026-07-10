(defproject temporal.sample.replay-testing "0.1.0-SNAPSHOT"
  :description "Demonstrates workflow replay testing with temporal.testing.history and temporal.testing.replayer"
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [io.github.manetu/temporal-sdk "2.0.1-SNAPSHOT"]
                 [com.taoensso/timbre "6.6.1"]]
  :target-path "target/%s"
  :source-paths ["src"]
  :test-paths ["test"]
  :resource-paths ["test/resources"]
  :profiles {:dev {:dependencies [[eftest "0.6.0"]]}})
