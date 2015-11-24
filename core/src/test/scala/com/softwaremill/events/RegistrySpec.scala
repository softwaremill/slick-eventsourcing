package com.softwaremill.events

import java.time.OffsetDateTime

import org.json4s.DefaultFormats
import org.scalatest.{FlatSpec, Matchers}
import slick.dbio.DBIO

class RegistrySpec extends FlatSpec with Matchers {
  it should "return event listeners in the order in which they were added" in {
    implicit val formats = DefaultFormats

    val el1: EventListener[String] = _ => DBIO.successful(Nil)
    val el2: EventListener[String] = _ => DBIO.successful(Nil)
    val el3: EventListener[String] = _ => DBIO.successful(Nil)

    val reg = Registry()
      .registerEventListener(el1)
      .registerAsyncEventListener(el3)
      .registerEventListener(el2)
      .registerAsyncEventListener(el1)

    val e = Event(0, "", "", 0, aggregateIsNew = false, OffsetDateTime.now(), 0L, 0, "")
    val listeners = reg.lookupEventListeners(e)
    val asyncListeners = reg.lookupAsyncEventListeners(e)

    listeners.length should be (2)

    listeners(0).eq(el1) should be (true)
    listeners(0).eq(el2) should be (false)

    listeners(1).eq(el1) should be (false)
    listeners(1).eq(el2) should be (true)

    asyncListeners.length should be (2)
    asyncListeners(0).eq(el3) should be (true)
    asyncListeners(1).eq(el1) should be (true)
  }
}