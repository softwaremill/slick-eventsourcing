package com.softwaremill.events

import java.util.concurrent.ConcurrentHashMap

import com.softwaremill.id.DefaultIdGenerator
import org.scalatest.concurrent.Eventually
import org.scalatest.{FlatSpec, Matchers}
import slick.dbio.DBIOAction

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}

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
      .registerModelUpdate[Event1] { e => DBIOAction.successful(()).map { r => actions :+= "mu1"; r } }
      .registerEventListener[Event1] { e => DBIOAction.successful(Nil).map { r => actions :+= "el1"; r } })

    implicit val hc = HandleContext.System
    val result = m.eventMachine.run(CommandResult.successful((), Event(Event1("x")).forNewAggregate)).futureValue
    val result1 = m.eventMachine.run(CommandResult.successful((), Event(Event1("y")).forNewAggregate)).futureValue

    val stack = new mutable.Stack[Try[Unit]]
    val pf: PartialFunction[Try[Unit], Unit] = { case dbResponse => stack.push(dbResponse) }

    // when
    val failOverFuture = m.eventMachine.failOverFromStoredEventsTo(logResult = pf)

    // then
    result should be(Right(()))
    eventually { stack shouldBe expectedStack }
  }

  private val expectedStack: mutable.Stack[Try[Unit]] = {
    val b = new mutable.Stack[Try[Unit]]
    b.push(Success(()))
    b.push(Success(()))
    b
  }
}

object EventMachineTest {
  case class Aggregate1()

  case class Event1(data: String)
  object Event1 extends AggregateForEvent[Event1, Aggregate1]

  case class Event2(data: String)
  object Event2 extends AggregateForEvent[Event2, Aggregate1]
}
