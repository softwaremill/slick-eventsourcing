package com.softwaremill.example

import java.time.Clock

import com.softwaremill.common.id.IdGenerator

import scala.concurrent.ExecutionContext

trait DefaultImplicits {
  implicit def clock: Clock
  implicit def idGenerator: IdGenerator
  implicit def ec: ExecutionContext
}
