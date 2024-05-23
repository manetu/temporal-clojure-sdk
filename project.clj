(defproject io.github.manetu/temporal-sdk "1.0.2-SNAPSHOT"
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
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [org.clojure/core.async "1.6.681"]
                 [io.temporal/temporal-sdk "1.23.2"]
                 [io.temporal/temporal-testing "1.23.2"]
                 [com.taoensso/encore "3.111.0"]
                 [com.taoensso/timbre "6.5.0"]
                 [com.taoensso/nippy "3.4.1"]
                 [funcool/promesa "9.2.542"]
                 [medley "1.4.0"]
                 [slingshot "0.12.2"]]
  :repl-options {:init-ns user}
  :java-source-paths ["src"]
  :javac-options ["-target" "11" "-source" "11"]

  :eastwood {:add-linters [:unused-namespaces]}
  :codox {:metadata {:doc/format :markdown}}

  :profiles {:dev {:dependencies   [[org.clojure/tools.namespace "1.5.0"]
                                    [eftest "0.6.0"]
                                    [mockery "0.1.4"]
                                    [io.temporal/temporal-opentracing "1.23.2"]]
                   :resource-paths ["test/temporal/test/resources"]}}
  :cloverage {:runner :eftest
              :runner-opts {:multithread? false
                            :fail-fast? true}
              :fail-threshold 90
              :ns-exclude-regex [#"temporal.client.worker"]})
