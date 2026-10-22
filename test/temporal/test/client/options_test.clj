(ns temporal.test.client.options-test
  (:require [clojure.test :refer [are deftest is]]
            [temporal.client.options :as sut])
  (:import [io.temporal.client WorkflowClientPlugin]
           [io.temporal.client.schedules ScheduleClientPlugin]
           [io.temporal.common.converter DataConverter DefaultDataConverter]
           [io.temporal.serviceclient WorkflowServiceStubsPlugin WorkflowServiceStubsOptions]))

(def MockDataConverter
  (reify DataConverter))

(deftest workflow-client-options->without-data-converter
  (are [data-converter-class options] (->> options
                                           (sut/workflow-client-options->)
                                           (.getDataConverter)
                                           (instance? data-converter-class))
    DefaultDataConverter {}
    DefaultDataConverter nil
    DataConverter {:data-converter MockDataConverter}))

(deftest schedule-client-options->without-data-converter
  (are [data-converter-class options] (->> options
                                           (sut/schedule-client-options->)
                                           (.getDataConverter)
                                           (instance? data-converter-class))
    DefaultDataConverter {}
    DefaultDataConverter nil
    DataConverter {:data-converter MockDataConverter}))

(deftest workflow-client-plugins
  (let [called (atom false)
        plugin (reify WorkflowClientPlugin
                 (getName [_] "test-plugin")
                 (configureWorkflowClient [_ _builder] (reset! called true)))
        opts   (sut/workflow-client-options-> {:plugins [plugin]})]
    (is (= 1 (count (.getPlugins opts))))
    (is (identical? plugin (first (.getPlugins opts))))))

(deftest schedule-client-plugins
  (let [called (atom false)
        plugin (reify ScheduleClientPlugin
                 (getName [_] "test-schedule-plugin")
                 (configureScheduleClient [_ _builder] (reset! called true)))
        opts   (sut/schedule-client-options-> {:plugins [plugin]})]
    (is (= 1 (count (.getPlugins opts))))
    (is (identical? plugin (first (.getPlugins opts))))))

(deftest stub-plugins
  (let [plugin (reify WorkflowServiceStubsPlugin
                 (getName [_] "test-stubs-plugin")
                 (configureServiceStubs [_ _builder])
                 (connectServiceClient [_ _opts supplier] (.get supplier)))
        opts   (sut/stub-options-> {:plugins [plugin]})]
    (is (= 1 (count (.getPlugins opts))))
    (is (identical? plugin (first (.getPlugins opts))))))
