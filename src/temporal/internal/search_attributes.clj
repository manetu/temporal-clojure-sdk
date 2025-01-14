(ns temporal.internal.search-attributes
  (:import [io.temporal.api.enums.v1 IndexedValueType]))

(def indexvalue-type->
  {:unspecified                 IndexedValueType/INDEXED_VALUE_TYPE_UNSPECIFIED
   :text                        IndexedValueType/INDEXED_VALUE_TYPE_TEXT
   :keyword                     IndexedValueType/INDEXED_VALUE_TYPE_KEYWORD
   :int                         IndexedValueType/INDEXED_VALUE_TYPE_INT
   :double                      IndexedValueType/INDEXED_VALUE_TYPE_DOUBLE
   :bool                        IndexedValueType/INDEXED_VALUE_TYPE_BOOL
   :datetime                    IndexedValueType/INDEXED_VALUE_TYPE_DATETIME
   :keyword-list                IndexedValueType/INDEXED_VALUE_TYPE_KEYWORD_LIST})
