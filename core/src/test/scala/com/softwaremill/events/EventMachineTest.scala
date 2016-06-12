package com.softwaremill.events

import java.time.ZoneOffset.UTC
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

import com.softwaremill.id.DefaultIdGenerator
import org.scalatest.concurrent.Eventually
import org.scalatest.{FlatSpec, Matchers}
import slick.dbio.DBIOAction

import scala.concurrent.ExecutionContext.Implicits.global

class EventMachineTest extends FlatSpec with Matchers with SqlSpec with Eventually {

  import EventMachineTest._

  def createModules(r: Registry) = new EventsModule {
    override implicit def ec = global
    override def idGenerator = new DefaultIdGenerator()
    override def registry = r
    override def eventsDatabase = database
  }

  it should "run model updates, then event listeners" in {
    // given
    var actions = Vector.empty[String]

    val m = createModules(Registry()
      .registerModelUpdate[Event1] { e => DBIOAction.successful(()).map { r => actions :+= "mu1"; r } }
      .registerEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => actions :+= "el1"; r } })

    // when
    implicit val hc = HandleContext.System
    val result = m.eventMachine.run(CommandResult.successful((), Event(Event1("x")).forNewAggregate)).futureValue

    // then
    result should be (Right(()))
    actions should be (Vector("mu1", "el1"))
  }

  it should "recursively run event listeners for events created by an event listener" in {
    // given
    var actions = Vector.empty[String]

    val m = createModules(Registry()
      .registerModelUpdate[Event1] { e => DBIOAction.successful(()).map { r => actions :+= "mu1"; r } }
      .registerModelUpdate[Event2] { e => DBIOAction.successful(()).map { r => actions :+= "mu2"; r } }
      .registerEventListener[Event1] { e =>
        DBIOAction.successful(List(Event(Event2("x")).forNewAggregate)).map { r => actions :+= "el1"; r }
      }
      .registerEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => actions :+= "el1_2"; r } }
      .registerEventListener[Event2] { e => DBIOAction.successful(Nil).map { r => actions :+= "el2"; r } })

    // when
    implicit val hc = HandleContext.System
    val result = m.eventMachine.run(CommandResult.successful((), Event(Event1("x")).forNewAggregate)).futureValue

    // then
    result should be (Right(()))
    actions should be (Vector("mu1", "el1", "mu2", "el2", "el1_2"))
  }

  it should "run async event listeners" in {
    // given
    val actions = new ConcurrentHashMap[String, Long]()
    def putAction(a: String) = actions.put(a, System.currentTimeMillis())

    val m = createModules(Registry()
      .registerEventListener[Event1] { e =>
        DBIOAction.successful(List(Event(Event2("x")).forNewAggregate)).map { r => putAction("el1"); r }
      }
      .registerAsyncEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => Thread.sleep(200L); putAction("el1a"); r } }
      .registerAsyncEventListener[Event2] { e => DBIOAction.successful(Nil).map { r => Thread.sleep(200L); putAction("el2a"); r } })

    m.asyncEventRunner.start()
    try {
      // when
      implicit val hc = HandleContext.System
      val result = m.eventMachine.run(CommandResult.successful((), Event(Event1("x")).forNewAggregate)).futureValue

      // then
      result should be(Right(()))
      eventually { actions should have size (3) }

      val el1 = actions.get("el1")
      val el1a = actions.get("el1a")
      val el2a = actions.get("el2a")

      (el1a - el1) should be > (100L)
      (el2a - el1) should be > (100L)
    }
    finally {
      m.asyncEventRunner.stop()
    }
  }

  it should "recover stored events" in {
    // given
    var actions = Vector.empty[String]

    val m = createModules(Registry()
      .registerModelUpdate[Event1] { e => DBIOAction.successful(()).map { r => actions :+= "mu1" + e.data.data; r } }
      .registerEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => actions :+= "el1"; r } })

    implicit val hc = HandleContext.System
    val result = m.eventMachine.run(CommandResult.successful((), Event(Event1("x")).forNewAggregate)).futureValue

    // when
    val failOverFuture = m.eventMachine.recoverStoredEvents().futureValue

    // then
    result should be(Right(()))
    actions should be (Vector("mu1x", "el1", "mu1x"))
  }

  it should "recover only one properly deserialized event and omit inappropriate" in {
    // given
    var actions = Vector.empty[String]

    val m = createModules(Registry()
      .registerModelUpdate[Event1] { e => DBIOAction.successful(()).map { r => actions :+= "mu1" + e.data.data; r } }
      .registerEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => actions :+= "el1"; r } })

    val now = OffsetDateTime.now()
    val eventType = "Event1"
    val aggregateType = "Aggregate1"
    val inappropriateEventBody = StoredEvent(1L, eventType, aggregateType, 2L, aggregateIsNew = true, now, 3L, 4L, "{\"login\":\"jan\"}")
    val properEvent = StoredEvent(5L, eventType, aggregateType, 6L, aggregateIsNew = true, now, 7L, 8L, "{\"data\":\"properData\"}")

    m.eventsDatabase.db.run(m.eventStore.store(inappropriateEventBody))
    m.eventsDatabase.db.run(m.eventStore.store(properEvent))
    implicit val hc = HandleContext.System

    // when
    val failOverFuture = m.eventMachine.recoverStoredEvents().futureValue

    // then
    actions should be(Vector("mu1properData"))
  }

  it should "recover single event" in {
    // given
    var actions = Vector.empty[String]

    val m = createModules(Registry()
      .registerModelUpdate[Event1] { e => DBIOAction.successful(()).map { r => actions :+= "singleMu1"; r } }
      .registerEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => actions :+= "singleEl1"; r } })

    val now = OffsetDateTime.now()
    val eventType = "Event1"
    val aggregateType = "singleAggregate1"
    val eventId: Long = 5L
    val properEvent = StoredEvent(eventId, eventType, aggregateType, 6L, aggregateIsNew = true, now, 7L, 8L, "{\"data\":\"properData\"}")

    m.eventsDatabase.db.run(m.eventStore.store(properEvent))
    implicit val hc = HandleContext.System

    // when
    val failOverFuture = m.eventMachine.recoverSingleStoredEvent(eventId).futureValue

    // then
    actions should be(Vector("singleMu1"))
  }

  it should "recover events from time range" in {
    //given
    var actions = Vector.empty[String]

    val m = createModules(Registry()
      .registerModelUpdate[Event1] { e => DBIOAction.successful(()).map { r => actions :+= "singleMu1"; r } }
      .registerEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => actions :+= "singleEl1"; r } })

    val now = OffsetDateTime.now()
    val eventType = "Event1"
    val aggregateType = "singleAggregate1"
    val eventId: Long = 5L
    val firstStoredEvent = StoredEvent(728110367896502272L, eventType, aggregateType, 6L, aggregateIsNew = true, now, 7L, 8L, "{\"data\":\"someData1\"}")
    val lastStoredEvent = StoredEvent(731734246552502272L, eventType, aggregateType, 9L, aggregateIsNew = true, now, 10L, 11L, "{\"data\":\"someData2\"}")
    val storedEventOverRange = StoredEvent(731734246552502273L, eventType, aggregateType, 9L, aggregateIsNew = true, now, 10L, 11L, "{\"data\":\"someData2\"}")

    m.eventsDatabase.db.run(m.eventStore.store(firstStoredEvent))
    m.eventsDatabase.db.run(m.eventStore.store(lastStoredEvent))
    m.eventsDatabase.db.run(m.eventStore.store(storedEventOverRange))
    implicit val hc = HandleContext.System
    val startDate = OffsetDateTime.of(2016, 5, 5, 6, 33, 34, 33, UTC)
    val endDate = OffsetDateTime.of(2016, 5, 15, 6, 33, 34, 33, UTC)

    //when
    val failOverFuture = m.eventMachine.recoverStoredEventsRange(startDate, endDate).futureValue

    //then
    actions should be(Vector("singleMu1", "singleMu1"))
  }
}

object EventMachineTest {
  case class Aggregate1()

  case class Event1(data: String)
  object Event1 extends AggregateForEvent[Event1, Aggregate1]

  case class Event2(data: String)
  object Event2 extends AggregateForEvent[Event2, Aggregate1]
}
