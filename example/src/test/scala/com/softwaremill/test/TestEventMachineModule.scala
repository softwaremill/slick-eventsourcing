package com.softwaremill.test

import com.softwaremill.id.DefaultIdGenerator
import com.softwaremill.events._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import slick.dbio.DBIO

trait TestEventMachineModule extends ScalaFutures with StrictLogging with EventsModule {
  lazy val idGenerator = new DefaultIdGenerator()
  implicit lazy val ec = scala.concurrent.ExecutionContext.Implicits.global

  implicit val patience = PatienceConfig(timeout = Span(1000, Millis))

  def runCommand[F, S](cr: CommandResult[F, S])(implicit hc: HandleContext) = {
    val f = eventMachine.run(cr)
    f.onFailure { case e: Exception => logger.error("Exception when running command", e) }
    f.futureValue
  }

  def runEvent[U, T](e: PartialEvent[U, T])(implicit hc: HandleContext) = {
    val f = eventMachine.run(CommandResult.successful((), e))
    f.onFailure { case e: Exception => logger.error("Exception when running event", e) }
    f.futureValue
  }
}

class EventSink[T] extends EventListener[T] {
  var events: List[Event[T]] = Nil

  override def apply(e: Event[T]) = {
    events = e :: events
    DBIO.successful(Nil)
  }
}