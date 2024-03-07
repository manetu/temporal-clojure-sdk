# Temporal Clojure SDK [![CircleCI](https://dl.circleci.com/status-badge/img/gh/manetu/temporal-clojure-sdk/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/manetu/temporal-clojure-sdk/tree/master)

[![Clojars Project](https://img.shields.io/clojars/v/io.github.manetu/temporal-sdk.svg)](https://clojars.org/io.github.manetu/temporal-sdk)

[Temporal](https://github.com/temporalio/temporal) is a Workflow-as-Code platform for building and operating
resilient applications using developer-friendly primitives instead of constantly fighting your infrastructure.

This Clojure SDK is a framework for authoring Workflows and Activities in Clojure.  (For other languages, see [Temporal SDKs](https://docs.temporal.io/application-development).)

### Status

**Alpha**

This SDK is battle-tested and used in production but is undergoing active development and is subject to breaking changes (*).  Some significant features such as Child-Workflows and Schedules are missing/incomplete.

> (*) We will always bump at least the minor version when breaking changes are introduced and include a release note.

### Clojure SDK

- [Clojure SDK and API documentation](https://cljdoc.org/d/io.github.manetu/temporal-sdk)
- [Clojure Samples](./samples/README.md)
- Presentations
    - [Boston Clojure Group Meetup 1/19/23](https://www.meetup.com/boston-clojure-group/events/290502741/)
        - [Video Recording](https://youtu.be/gztsbSP5I3s)
        - [Slides](https://docs.google.com/presentation/d/1D7cd4UUI_6ZEzd7RbSgujB-5PsD-EDGE)
- [Clojurian Slack Channel](https://clojurians.slack.com/archives/C056TDVQ5L1)

### Temporal, in general

- [Temporal docs](https://docs.temporal.io/)
- [Install Temporal Server](https://docs.temporal.io/docs/server/quick-install)
- [Temporal CLI](https://docs.temporal.io/docs/devtools/tctl/)

## Requirements

- JDK 11+

## Contributing

Pull requests are welcome.  Please include a [DCO](https://en.wikipedia.org/wiki/Developer_Certificate_of_Origin) in any commit messages.

## License

Copyright (C) Manetu, Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this material except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
