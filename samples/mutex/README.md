# temporal.sample.mutex

Based upon the [Go SDK Mutex Sample](https://github.com/temporalio/samples-go/tree/main/mutex)

This mutex workflow demos an ability to lock/unlock a particular resource within a particular Temporal namespace so that other workflows within the same namespace would wait until a resource lock is released. This is useful when we want to avoid race conditions or parallel mutually exclusive operations on the same resource.

One way of coordinating parallel processing is to use Temporal signals with [temporal.client.core/signal-with-start](https://cljdoc.org/d/io.github.manetu/temporal-sdk/CURRENT/api/temporal.client.core#signal-with-start) and make sure signals are getting processed sequentially, however the logic might become too complex if we need to lock two or more resources at the same time. Mutex workflow pattern can simplify that.

This example enqueues several long-running workflows in parallel, each of which has a Mutex section. When the workflow reaches the Mutex section, it starts a mutex workflow via local activity, and blocks until acquire-lock-event is received. Once acquire-lock-event is received, it enters critical section, and finally releases the lock once processing is over by sending a release signal to the Mutex Workflow.
