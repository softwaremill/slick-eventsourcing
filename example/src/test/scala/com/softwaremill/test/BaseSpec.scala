package com.softwaremill.test

import java.time.Clock

import com.softwaremill.common.id.DefaultIdGenerator
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{FlatSpec, Matchers}

trait BaseSpec extends FlatSpec with Matchers with ScalaFutures with TestImplicits {
  implicit val patience = PatienceConfig(timeout = Span(1000, Millis))
}

trait TestImplicits {
  implicit lazy val idGenerator = new DefaultIdGenerator()
  implicit lazy val clock = Clock.systemUTC()
  implicit lazy val ec = scala.concurrent.ExecutionContext.Implicits.global
}