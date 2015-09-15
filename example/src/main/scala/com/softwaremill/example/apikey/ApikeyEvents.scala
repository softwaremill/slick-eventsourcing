package com.softwaremill.example.apikey

import com.softwaremill.events.AggregateForEvent

case class ApikeyCreated(apikey: String)
object ApikeyCreated {
  implicit val afe = AggregateForEvent[ApikeyCreated, Apikey]
}

case class ApikeyDeleted()
object ApikeyDeleted {
  implicit val afe = AggregateForEvent[ApikeyDeleted, Apikey]
}