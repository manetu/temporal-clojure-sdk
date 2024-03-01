;; Copyright © 2024 Manetu, Inc.  All rights reserved

(ns temporal.exceptions)

(def flags {::non-retriable? "'true' indicates this exception is not going to be retried even if it is not included into retry policy doNotRetry list."})
