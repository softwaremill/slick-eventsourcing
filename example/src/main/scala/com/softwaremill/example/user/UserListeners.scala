package com.softwaremill.example.user

import com.softwaremill.events.{EventListener, ModelUpdate}
import com.softwaremill.example.apikey.ApikeyCommands
import com.softwaremill.example.email.EmailService
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext

class UserListeners(userModel: UserModel)(implicit ec: ExecutionContext) {
  val createApiKey: ApikeyCommands => EventListener[UserRegistered] = apikeyCommands => e =>
    apikeyCommands.create().map(_._2)

  val sendEmail: EmailService => EventListener[UserRegistered] = { emailService => e =>
    emailService.sendEmail(e.data.email, "Welcome!")
    DBIO.successful(Nil)
  }

  val registeredUpdate: ModelUpdate[UserRegistered] = { e =>
    val user = User(e.aggregateId, e.data.login, e.data.login.toLowerCase, e.data.email.toLowerCase, e.data.encryptedPassword,
      e.data.salt, None)
    userModel.updateNew(user)
  }

  val lastLoginUpdate: ModelUpdate[UserLoggedIn] = e => userModel.updateLastLogin(e.aggregateId, e.created)
}
