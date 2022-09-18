# Temporal Clojure SDK [![CircleCI](https://dl.circleci.com/status-badge/img/gh/manetu/temporal-clojure-sdk/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/manetu/temporal-clojure-sdk/tree/master)

[![Clojars Project](https://img.shields.io/clojars/v/io.github.manetu/temporal-sdk.svg)](https://clojars.org/io.github.manetu/temporal-sdk)

[Temporal](https://github.com/temporalio/temporal) is a Workflow-as-Code platform for building and operating
resilient applications using developer-friendly primitives, instead of constantly fighting your infrastructure.

This Clojure SDK is a framework for authoring Workflows and Activities in Clojure. (For other languages, see [Temporal SDKs](https://docs.temporal.io/application-development).)

### Status

**Alpha**

This SDK is functional, but undergoing active development.  Some major features (Queries, Child-Workflows, SideEffects, etc) are missing/incomplete.  API may evolve in breaking ways, though we will always bump at least the minor version when this happens and include a note.

### Clojure SDK

- [Clojure SDK and API documentation](https://cljdoc.org/d/io.github.manetu/temporal-sdk)
- [Clojure Samples](./samples/README.md)

### Temporal in general

- [Temporal docs](https://docs.temporal.io/)
- [Install Temporal Server](https://docs.temporal.io/docs/server/quick-install)
- [Temporal CLI](https://docs.temporal.io/docs/devtools/tctl/)

## Requirements

- JDK 11+

## Contributing

Pull requests welcome.  Please be sure to include a [DCO](https://en.wikipedia.org/wiki/Developer_Certificate_of_Origin) in any commit messages.

## License

Copyright (C) 2022 Manetu, Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this material except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
