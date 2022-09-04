(defproject io.github.manetu/temporal-sdk "0.2.0-SNAPSHOT"
  :description "A Temporal SDK for Clojure"
  :url "https://github.com/manetu/temporal-clojure-sdk"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :year 2022
            :key "apache-2.0"}
  :plugins [[lein-cljfmt "0.8.2"]
            [lein-kibit "0.1.8"]
            [lein-bikeshed "0.5.2"]
            [lein-cloverage "1.2.4"]
            [jonase/eastwood "1.2.4"]
            [lein-codox "0.10.8"]]
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.temporal/temporal-sdk "1.16.0"]
                 [io.temporal/temporal-testing "1.16.0"]
                 [com.taoensso/timbre "5.2.1"]
                 [funcool/promesa "8.0.450"]]
  :repl-options {:init-ns user}
  :aot [temporal.internal.dispatcher]

  :eastwood {:add-linters [:unused-namespaces]}
  :codox {:metadata {:doc/format :markdown}}

  :profiles {:dev {:dependencies   [[org.clojure/tools.namespace "1.3.0"]
                                    [eftest "0.5.9"]]}}
  :cloverage {:runner :eftest
              :runner-opts {:multithread? false
                            :fail-fast? true}
              :fail-threshold 91
              :ns-exclude-regex [#"temporal.client.worker"]})
