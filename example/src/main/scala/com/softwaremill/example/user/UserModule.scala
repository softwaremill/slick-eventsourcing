package com.softwaremill.example.user

import com.softwaremill.id.IdGenerator
import com.softwaremill.database.SqlDatabase
import com.softwaremill.events.Registry
import com.softwaremill.example.email.EmailService
import com.softwaremill.example.apikey.ApikeyCommands

import scala.concurrent.ExecutionContext

trait UserModule {
  lazy val userCommands = new UserCommands(userModel, idGenerator)
  lazy val userListeners = new UserListeners(userModel)
  lazy val userModel = new UserModel(sqlDatabase)

  def addUserListeners = (_: Registry)
    .registerModelUpdate(userListeners.registeredUpdate)
    .registerModelUpdate(userListeners.lastLoginUpdate)
    .registerEventListener(userListeners.createApiKey(apikeyCommands))
    .registerEventListener(userListeners.sendEmail(emailService))

  def apikeyCommands: ApikeyCommands
  def emailService: EmailService
  def sqlDatabase: SqlDatabase
  def idGenerator: IdGenerator

  implicit def ec: ExecutionContext
}
