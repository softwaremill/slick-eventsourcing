package com.softwaremill.events
import java.time.OffsetDateTime

import org.json4s.{DefaultFormats, MappingException}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.util.Failure

case class TestUser()
case class SingleParamEvent(login: String)
object SingleParamEvent {
  implicit val afe = AggregateForEvent[SingleParamEvent, TestUser]
}
case class DoubleParamEvent(name: String, login: String)
object DoubleParamEvent {
  implicit val afe = AggregateForEvent[DoubleParamEvent, TestUser]
}

class StoredEventTest extends FlatSpec with Matchers with BeforeAndAfter {
  val now = OffsetDateTime.now()
  val eventType = "sampleEventType"
  val aggreateType = "sampleAggregateType"
  val storedEvent = StoredEvent(1L, eventType, aggreateType, 2L, aggregateIsNew = true, now, 3L, 4L,
    "{\"login\":\"jan\"}")

  it should "successfully parse json to event" in {
    //when
    val result = storedEvent.toEvent(classOf[SingleParamEvent], DefaultFormats)

    //then
    val expected = Event(1L, eventType, aggreateType, 2L, aggregateIsNew = true, now, 3L, 4L, SingleParamEvent("jan"))
    implicit val afe = AggregateForEvent[Any, TestUser]

    result.get.aggregateId[TestUser] should be(expected.aggregateId)
    result.get.toString should be(expected.toString)
  }

  it should "return failure when parsing json with missing values" in {
    //when
    val result = storedEvent.toEvent(classOf[DoubleParamEvent], DefaultFormats).isInstanceOf[Failure[MappingException]]
    //then
    result should be(true)
  }

}