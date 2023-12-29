;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.tls
  "Utilities for connecting to TLS enabled Temporal clusters"
  (:require [clojure.java.io :as io])
  (:import [java.security KeyStore]
           [java.security.cert CertificateFactory X509Certificate]
           [javax.net.ssl TrustManagerFactory]
           [io.grpc.netty.shaded.io.grpc.netty GrpcSslContexts]
           [io.grpc.netty.shaded.io.netty.handler.ssl SslContext]))

(defn- new-ca
  ^X509Certificate [certpath]
  (let [cf (CertificateFactory/getInstance "X.509")]
    (with-open [is (io/input-stream certpath)]
      (.generateCertificate cf is))))

(defn- new-keystore
  ^KeyStore [certpath]
  (let [ca (new-ca certpath)
        ks-type (KeyStore/getDefaultType)]
    (doto (KeyStore/getInstance ks-type)
      (.load nil nil)
      (.setCertificateEntry "ca" ca))))

(defn- new-trustmanagerfactory
  ^TrustManagerFactory [certpath]
  (let [alg (TrustManagerFactory/getDefaultAlgorithm)
        ks (new-keystore certpath)]
    (doto (TrustManagerFactory/getInstance alg)
      (.init ks))))

(defn new-ssl-context
  "
Creates a new gRPC [SslContext](https://netty.io/4.0/api/io/netty/handler/ssl/SslContext.html) suitable for passing to the :ssl-context option of [[temporal.client.core/create-client]]

Arguments:

- `ca-path`:   The path to a PEM encoded x509 Certificate Authority root certificate for validating the Temporal server.
- `cert-path`: The path to a PEM encoded x509 Certificate representing this client's identity, used for mutual TLS authentication.
- 'key-path':  The path to a PEM encoded private key representing this client's identity, used for mutual TLS authentication.

"
  ^SslContext [{:keys [ca-path cert-path key-path] :as args}]
  (-> (GrpcSslContexts/forClient)
      (cond->
       (some? ca-path) (.trustManager (new-trustmanagerfactory ca-path))
       (and (some? cert-path) (some? key-path)) (.keyManager (io/file cert-path) (io/file key-path)))
      (.build)))
