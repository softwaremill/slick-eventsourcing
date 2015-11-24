package com.softwaremill.example.apikey

import com.softwaremill.database.SqlDatabase
import com.softwaremill.events.Registry

import scala.concurrent.ExecutionContext

trait ApikeyModule {
  lazy val apikeyCommands = new ApikeyCommands(apikeyModel)
  lazy val apikeyListeners = new ApikeyListeners(apikeyModel)
  lazy val apikeyModel = new ApikeyModel(sqlDatabase)

  def addApikeyListeners = (_: Registry)
    .registerModelUpdate(apikeyListeners.createdUpdated)
    .registerModelUpdate(apikeyListeners.deletedUpdated)

  def sqlDatabase: SqlDatabase

  implicit def ec: ExecutionContext
}
