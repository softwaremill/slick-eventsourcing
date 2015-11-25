package com.softwaremill.example.apikey

import com.softwaremill.events.{EventsDatabase, Registry}

import scala.concurrent.ExecutionContext

trait ApikeyModule {
  lazy val apikeyCommands = new ApikeyCommands(apikeyModel)
  lazy val apikeyListeners = new ApikeyListeners(apikeyModel)
  lazy val apikeyModel = new ApikeyModel(eventsDatabase)

  def addApikeyListeners = (_: Registry)
    .registerModelUpdate(apikeyListeners.createdUpdated)
    .registerModelUpdate(apikeyListeners.deletedUpdated)

  def eventsDatabase: EventsDatabase

  implicit def ec: ExecutionContext
}
