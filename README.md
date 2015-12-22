`slick-eventsourcing` is a library/framework which implements a specific approach to event sourcing, using a relational database. See [this blog](https://softwaremill.com/entry-level-event-sourcing/) for an introduction.

Using `slick-eventsourcing` you can write applications which take advantage of transactional database features such as consistency and SQL query capabilities and in addition contain (or, in fact, are driven by) a **full audit log** of all actions in the system.

[![Build Status](https://travis-ci.org/softwaremill/slick-eventsourcing.svg)](https://travis-ci.org/softwaremill/slick-eventsourcing)
[![Join the chat at https://gitter.im/softwaremill/slick-eventsourcing](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/softwaremill/slick-eventsourcing?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.events/core_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.softwaremill.events/core_2.11)
[![Dependencies](https://app.updateimpact.com/badge/634276070333485056/slick-eventsourcing.svg?config=compile)](https://app.updateimpact.com/latest/634276070333485056/slick-eventsourcing)

# Example

If you'd like to see a simple application written using the framework, see the `example` directory in the sources. You can run it with `./example-start.sh` and then pointing you web browser to [localhost:8080](http://localhost:8080). Observe the console output for events that are created and handled.

# Model

**Events** are the main source of truth in the system and contain information on what *happened* (past tense!) in the system. Events are usually created in response to user actions, but can also be created in response to other events, or external triggers in the system.

Basing on events, a **read model** is created. The read model is used to validate user actions before creating new events and to provide data to user queries. The model consists of ordinary SQL tables. The main difference to a traditional "CRUD" application is that updates occur *only* basing on events, not directly as a result to user actions.

There are three main types of functions involved:

* *model update* functions: take an event and update the read model accordingly.
* *event listeners*: take an event and run business logic. Can create other events, which are handled recursively.
* *commands*: take user input, validate it and create events.

Model update functions can be used to re-create the read model if needed (or create an updated read model), hence they shouldn't have any side-effects other than updating the model.

Event listeners are supposed to be run only when the event is first handled, hence that's where side-effects should occur.

## Commands

The main way events are created is through **commands**. A command is a plain Scala function which accepts some input data and returns a result consisting of: a success or failure value (represented as a `Either[F, S]`) and a list of emitted events (`List[PartialEvent[_, _]`; partial, as not all event metadata is provided upfront; e.g. event and transaction ids are filled in automatically later). As commands can use the read model to validate the input, everything is wrapped in a [`DBIOAction`](http://slick.typesafe.com/doc/3.0.0/dbio.html). Hence a `CommandResult` is a:

````scala
type CommandResult[F, S] = DBIOAction[(Either[F, S], List[PartialEvent[_, _]]), NoStream, Read]
````

Note that when creating a command result, we are returning a *description* (`DBIOAction`) of how the database should be queried; the real queries will be invoked only when the action is *run*. Such descriptions are ordinary immutable Scala values, can be re-used and shared multiple times.

## Model update & event listener functions

The model update and event listener functions have similar signatures:

````scala
type EventListener[T] = Event[T] => DBIOAction[List[PartialEvent[_, _]], NoStream, Read]
type ModelUpdate[T] = Event[T] => DBIOAction[Unit, NoStream, Read with Write]
````

Model update doesn't return any values, and can read & write the model. Event listeners can only read, but return a list of partial events. Both results are wrapped, as with `CommandResult`, in a `DBIOAction`, so they return a *description* of what should be done, and that is only run later.

If you would like to run some side-effects in an event listener, e.g. communicating over the network with an external service, you can wrap a `Future[T]` into a `DBIOAction[T]` with the `DBIO.from` method.

## Registry

You need to specify what kind of model update and event listeners you would like to be run in response to events. This is done through a `Registry`, which is an immutable class and contains three builder methods:

* `registerModelUpdate(h: ModelUpdate[T])`
* `registerEventListener(h: EventListener[T])`
* `registerAsyncEventListener(h: EventListener[T])`

The difference between regular (synchronous) and asynchronous event listeners will be discussed later.

You can also specify the default `org.json4s.Formats` to use to serialize/deserialize event data, and define event-specific formats using the `registerFormats[T](formats)` method (where `T` is the type of the event).

## Event machine

Given a registry, it is possible to create an `EventMachine`. The event machine is responsible for running appropriate actions when events are created. The main method in the event machine is:

````scala
def run[F, S](cr: CommandResult[F, S]): Future[Either[F, S]]
````

This method takes a command result, runs the actions that are described by it using the database and returns the result to the user. Any events that are created are first persisted in the database, and then handled by first running all appropriate model update functions, and then event listeners. If the event listeners create more events (e.g. by returning the result of running other commands), they are handled recursively (by first persisting them, running model update, then event listeners, etc.)

## Transactions

What's very important for data consistency is that the database actions needed to compute the command result, persist the events, run the model update functions and event listeners are all done in a single transaction. So if at any point there is a failure, either due to an exception in the business logic, or if a constraint in the database fails, nothing will be persisted.

The exception here are asynchronous event listeners (registered with `registerAsyncEventListener`), which are run after the transaction completes, in the background. Such event listeners are useful if some logic needs to be run when an event is created, but without blocking the user from receiving a reply or the transaction from committing. If an asynchronous event listener creates more events, they are handled in a single transaction with the original event (so there's one additional transaction for each event and asynchronous event listener)

# How to create events

An event consist of arbitrary event data and some meta-data. The event data is usually a case class, and is serialised to JSON when written to the database. Each event is associated with an aggregate (a "root entity") through an id. To define what's the aggregate for an event, an implicit `AggregateForEvent[T, U]` must be available (typically defined in the companion object for the event's data), where `T` is the type of the event data and `U` is type of the aggregate.

To create an event you should use the `Event(eventData)` method, which takes an implicit `AggregateForEvent`. Then, you need to specify what's the id of the aggregate, either by using `forAggregate`, or `forNewAggregate`, if the id is not yet available.

That way you will obtain a `PartialEvent` which can be returned from a command or an event listener.

Before persisting, other meta-data will be generated for the event:

* `id`
* `eventType`: corresponds to the class of event data
* `aggregateType`: name of the aggregate
* `aggregateId`
* `isNew`: a flag if the aggregate is new, or is the event modifying an existing one
* `created`: timestamp for the event
* `userId`: the id of the user which created the event (if any)
* `txId`: id of the transaction, all events created within a single transaction will have the same id

# Ids

Aggregate and event ids are created by an `IdGenerator`, which is needed when creating the `EventMachine`. The id generator generates time-based unique `Long` ids. If there are multiple nodes, each `IdGenerator` should be created with a different `workerId`.

The ids are [type-tagged](https://github.com/softwaremill/scala-common), so that at runtime they are simple `Long`s, however at compile-time they are type-safe and bound to a specific aggregate/entity. For example `Long @@ User` is a long value tagged with the `User` type; it cannot be used e.g. where a `Long @@ Product` is expected. To tag a value, you can use the `.taggedWith[T]` method.

To properly type-tag methods which return or expect a user id (e.g. to invoke `Event.userId`), an implicit instance of `UserType[T]` should be in scope. There should be only one instance of that type in the whole system, and it should be parametrised by the type of the user entity.

# Handle context

When invoking `EventMachine.run`, an implicit `HandleContext` is needed. The handle context contains the id of the currently logged in user, so that it can be associated with events that are created. If no user is logged in, `HandleContext.System` should be used. Otherwise, before invoking `run`, declare an implicit handle context using the one-arg constructor.

As each system will have its own notion of a user, the user type needs to be provided by an implicit `UserType[T]` value. There should only be a single, implicit instance of `UserType`, parametrised by the actual type of the user aggregate. That implicit is needed when obtaining the id of the user who created the event (to provide the appropriate type tag), and when creating a handle context.

If an event causes a user to be logged in (e.g. a user registered or user logged in event), it should implement the `HandleContextTransform` method, which should add the user id to the context.

# Using from SBT

````scala
libraryDependencies += "com.softwaremill.events" %% "core" % "0.1.2"
````

# How to recover model state 

In case you lost your database model but got event log, you can rebuild from the very beginning up to some point in time. There is a manual tool called `RecoverDbState` which calls eventMachine's `failOverFromStoredEvents` with default parameter of type `OffsetDateTime` - this indicated `until`. 

# Version history

* 25/11/2015, 0.1: initial release
* 27/11/2015, 0.1.1: bug fix
* 30/11/2015, 0.1.2: updating to akka-http 2.0-m2
* 7/12/2015, 0.1.3: making `EventStore` a trait, changing param type in `EventsDatabase`
* 15/12/2015, 0.1.4: initial recovery support
* 21/12/2015, 0.1.5: registering custom event formats, updating to akka-http 2.0