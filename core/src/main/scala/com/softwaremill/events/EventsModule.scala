package com.softwaremill.events

import java.time.Clock
import java.util.concurrent.LinkedBlockingQueue

import com.softwaremill.common.id.IdGenerator
import com.softwaremill.database.SqlDatabase

import scala.concurrent.ExecutionContext

/**
  * Default wiring of the classes involved in handling the events.
  */
trait EventsModule {
  lazy val eventStore = new EventStore(sqlDatabase)
  lazy val asyncEventQueue = new LinkedBlockingQueue[Event[_]]()
  lazy val asyncEventScheduler = new BlockingQueueAsyncEventScheduler(asyncEventQueue)
  lazy val eventMachine = new EventMachine(sqlDatabase, registry, eventStore, asyncEventScheduler)
  lazy val asyncEventRunner = new BlockingQueueAsyncEventRunner(asyncEventQueue, eventMachine)

  implicit def clock: Clock
  implicit def idGenerator: IdGenerator
  implicit def ec: ExecutionContext

  def sqlDatabase: SqlDatabase
  def registry: Registry
}
