(ns temporal.internal.search-attributes
  (:import [io.temporal.api.enums.v1 IndexedValueType]
           [io.temporal.common SearchAttributeKey SearchAttributeUpdate]))

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
  (into-array SearchAttributeUpdate
              (map (fn [[name {:keys [type value]}]]
                     (make-search-attribute-update name type value))
                   attrs)))
