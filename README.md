# Metronom

Metronom is a simple API that tries to dispatch events to a lambda at the most accurate and stable rhythm it possibly
can with the limitations implied by the Java threading model.

You configure the rate at which you want to be notified, and you will get events with a bellow-millisecond accuracy. The
implementation automatically compensates the time of execution taken by the lambda to process.

You can also be notified about missed "tics", when the desired tempo and the execution time taken by the client code led
to the impossibility to dispatch events fast enough.

The client code is always executed by the same thread, and each instance of ch.sebpiller.tictac.Metronome creates its
own. Further enhancements could make use of several threads to dispatch the load, in case a higher guarantee that no
events can be missed. Since such a feature will unnecessarily complexify users' code to manage multithreading, and since
this is not needed for any of my use cases, this is not planed yet.

In short, it does the annoying job required to get high precision time-based events with the Java platform.