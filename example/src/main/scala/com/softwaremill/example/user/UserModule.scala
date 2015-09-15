package com.softwaremill.example.user

import com.softwaremill.database.SqlDatabase
import com.softwaremill.events.Registry
import com.softwaremill.example.DefaultImplicits
import com.softwaremill.example.email.EmailService
import com.softwaremill.macwire._
import com.softwaremill.example.apikey.ApikeyCommands

trait UserModule extends DefaultImplicits {
  lazy val userCommands = wire[UserCommands]
  lazy val userListeners: UserListeners = wire[UserListeners]
  lazy val userModel = wire[UserModel]

  def addUserListeners = (_: Registry)
    .registerModelUpdate(userListeners.registeredUpdate)
    .registerModelUpdate(userListeners.lastLoginUpdate)
    .registerEventListener(userListeners.createApiKey(apikeyCommands))
    .registerEventListener(userListeners.sendEmail(emailService))

  def apikeyCommands: ApikeyCommands
  def emailService: EmailService
  def sqlDatabase: SqlDatabase
}
