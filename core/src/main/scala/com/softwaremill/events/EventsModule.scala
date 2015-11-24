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
  lazy val eventMachine = new EventMachine(sqlDatabase, registry, eventStore, asyncEventScheduler, idGenerator, clock)
  lazy val asyncEventRunner = new BlockingQueueAsyncEventRunner(asyncEventQueue, eventMachine)
  lazy val clock = Clock.systemUTC()

  implicit def ec: ExecutionContext

  def idGenerator: IdGenerator
  def sqlDatabase: SqlDatabase
  def registry: Registry
}
