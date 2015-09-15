package com.softwaremill.example.apikey

import com.softwaremill.example.common.Utils
import com.softwaremill.events.{CommandResult, Event}

import scala.concurrent.ExecutionContext

class ApikeyCommands(apikeyModel: ApikeyModel)(implicit ec: ExecutionContext) {
  def create(): CommandResult[Nothing, String] = {
    val eventData = ApikeyCreated(Utils.randomString(32))
    CommandResult.successful(eventData.apikey, Event(eventData).forNewAggregate)
  }

  def delete(apikey: String): CommandResult[Nothing, Unit] = {
    apikeyModel.findByApikey(apikey).flatMap {
      case Some(ak) =>
        val event = Event(ApikeyDeleted()).forAggregate(ak.id)
        CommandResult.successful((), event)
      case None => CommandResult.successful(())
    }
  }
}
