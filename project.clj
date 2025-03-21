(defproject io.github.manetu/temporal-sdk "1.3.1-SNAPSHOT"
  :description "A Temporal SDK for Clojure"
  :url "https://github.com/manetu/temporal-clojure-sdk"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :year 2023
            :key "apache-2.0"}
  :plugins [[lein-cljfmt "0.9.0"]
            [lein-kibit "0.1.8"]
            [lein-bikeshed "0.5.2"]
            [lein-cloverage "1.2.4"]
            [jonase/eastwood "1.3.0"]
            [lein-codox "0.10.8"]]
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/core.async "1.7.701"]
                 [io.temporal/temporal-sdk "1.28.3"]
                 [io.temporal/temporal-testing "1.28.3"]
                 [com.taoensso/encore "3.139.0"]
                 [com.taoensso/timbre "6.6.1"]
                 [com.taoensso/nippy "3.4.2"]
                 [funcool/promesa "9.2.542"]
                 [medley "1.4.0"]
                 [slingshot "0.12.2"]]
  :repl-options {:init-ns user}
  :java-source-paths ["src" "resources"]
  :javac-options ["-target" "11" "-source" "11"]

  :eastwood {:add-linters [:unused-namespaces]}
  :codox {:metadata {:doc/format :markdown}}

  :profiles {:dev {:dependencies   [[org.clojure/tools.namespace "1.5.0"]
                                    [eftest "0.6.0"]
                                    [mockery "0.1.4"]
                                    [io.temporal/temporal-opentracing "1.28.3"]]
                   :resource-paths ["test/temporal/test/resources"]}}
  :cloverage {:runner :eftest
              :runner-opts {:multithread? false
                            :fail-fast? true}
              :fail-threshold 91
              :ns-exclude-regex [#"temporal.client.worker"]})
