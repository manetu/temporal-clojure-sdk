(ns temporal.internal.search-attributes
  (:require [clojure.set :as set])
  (:import [io.temporal.api.enums.v1 IndexedValueType]
           [io.temporal.common SearchAttributeKey SearchAttributeUpdate SearchAttributes]))

(def indexvalue-type->
  {:unspecified                 IndexedValueType/INDEXED_VALUE_TYPE_UNSPECIFIED
   :text                        IndexedValueType/INDEXED_VALUE_TYPE_TEXT
   :keyword                     IndexedValueType/INDEXED_VALUE_TYPE_KEYWORD
   :int                         IndexedValueType/INDEXED_VALUE_TYPE_INT
   :double                      IndexedValueType/INDEXED_VALUE_TYPE_DOUBLE
   :bool                        IndexedValueType/INDEXED_VALUE_TYPE_BOOL
   :datetime                    IndexedValueType/INDEXED_VALUE_TYPE_DATETIME
   :keyword-list                IndexedValueType/INDEXED_VALUE_TYPE_KEYWORD_LIST})

(defn make-search-attribute-key
  "Creates a SearchAttributeKey for the given name and type.
   Type can be :text, :keyword, :int, :double, :bool, :datetime, or :keyword-list"
  ^SearchAttributeKey [name type]
  (case type
    :text         (SearchAttributeKey/forText name)
    :keyword      (SearchAttributeKey/forKeyword name)
    :int          (SearchAttributeKey/forLong name)
    :double       (SearchAttributeKey/forDouble name)
    :bool         (SearchAttributeKey/forBoolean name)
    :datetime     (SearchAttributeKey/forOffsetDateTime name)
    :keyword-list (SearchAttributeKey/forKeywordList name)
    (throw (IllegalArgumentException. (str "Unknown search attribute type: " type)))))

(defn typed-search-attribute?
  [value]
  (and (map? value)
       (contains? value :type)
       (contains? value :value)))

(defn- keyword-list?
  [value]
  (and (sequential? value)
       (every? (some-fn string? ident?) value)))

(defn- ->keyword-string
  [value]
  (if (ident? value)
    (name value)
    value))

(defn infer-search-attribute
  [attr-name value]
  (cond
    (typed-search-attribute? value)
    value

    (nil? value)
    {:type :keyword :value nil}

    (string? value)
    {:type :text :value value}

    (or (keyword? value) (symbol? value))
    {:type :keyword :value (name value)}

    (integer? value)
    {:type :int :value (long value)}

    (number? value)
    {:type :double :value (double value)}

    (boolean? value)
    {:type :bool :value value}

    (instance? java.time.OffsetDateTime value)
    {:type :datetime :value value}

    (keyword-list? value)
    {:type :keyword-list :value (mapv ->keyword-string value)}

    :else
    (throw (IllegalArgumentException.
            (str "Unsupported search attribute value for key " attr-name ": " (pr-str value))))))

(defn normalize-search-attributes
  "Normalizes :search-attributes input into typed maps.

   Accepts both simple values and typed maps, returning:
   {\"Name\" {:type t :value v}}

   Inference rules for simple values:
   - string -> :text
   - keyword/symbol -> :keyword
   - integer -> :int
   - number -> :double
   - boolean -> :bool
   - OffsetDateTime -> :datetime
   - sequence of string/keyword/symbol -> :keyword-list"
  [attrs]
  (into {}
        (map (fn [[name value]] [name (infer-search-attribute name value)]))
        attrs))

(defn make-search-attribute-update
  "Creates a SearchAttributeUpdate for setting or unsetting a value.
   If value is nil, creates an unset update. Otherwise creates a valueSet update."
  ^SearchAttributeUpdate [name type value]
  (let [key (make-search-attribute-key name type)]
    (if (nil? value)
      (SearchAttributeUpdate/valueUnset key)
      (SearchAttributeUpdate/valueSet key value))))

(defn search-attribute-updates->
  "Converts a map of {name {:type type :value value}} to an array of SearchAttributeUpdate objects"
  ^"[Lio.temporal.common.SearchAttributeUpdate;" [attrs]
  (let [normalized (normalize-search-attributes attrs)]
    (into-array SearchAttributeUpdate
                (map (fn [[name {:keys [type value]}]]
                       (make-search-attribute-update name type value))
                     normalized))))

(defn search-attributes->
  "Converts search-attributes input to typed SearchAttributes."
  ^SearchAttributes [attrs]
  (let [builder (SearchAttributes/newBuilder)
        normalized (normalize-search-attributes attrs)]
    (doseq [[name {:keys [type value]}] normalized]
      (let [^SearchAttributeKey key (make-search-attribute-key name type)]
        (if (nil? value)
          (.unset builder key)
          (.set builder key value))))
    (.build builder)))

(defn search-attributes->map
  "Converts typed SearchAttributes to {name {:type type :value value}}."
  [^SearchAttributes attrs]
  (let [type-indexvalue-> (set/map-invert indexvalue-type->)]
    (into {}
          (map (fn [[^SearchAttributeKey key value]]
                 [(.getName key)
                  {:type (type-indexvalue-> (.getValueType key))
                   :value value}]))
          (.getUntypedValues attrs))))
