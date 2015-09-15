package com.softwaremill.example.apikey

import com.softwaremill.database.SqlDatabase
import com.softwaremill.events.Registry
import com.softwaremill.example.DefaultImplicits
import com.softwaremill.macwire._

trait ApikeyModule extends DefaultImplicits {
  lazy val apikeyCommands = wire[ApikeyCommands]
  lazy val apikeyListeners: ApikeyListeners = wire[ApikeyListeners]
  lazy val apikeyModel = wire[ApikeyModel]

  def addApikeyListeners = (_: Registry)
    .registerModelUpdate(apikeyListeners.createdUpdated)
    .registerModelUpdate(apikeyListeners.deletedUpdated)

  def sqlDatabase: SqlDatabase
}
