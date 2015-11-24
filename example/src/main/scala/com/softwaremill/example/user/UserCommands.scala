package com.softwaremill.example.user

import com.softwaremill.example.common.Utils
import com.softwaremill.events.{CommandResult, Event}
import com.softwaremill.macwire.tagging._
import com.typesafe.scalalogging.StrictLogging
import com.softwaremill.common.id.IdGenerator

import scala.concurrent.ExecutionContext

class UserCommands(userModel: UserModel)(implicit ec: ExecutionContext, idGenerator: IdGenerator) extends StrictLogging {
  import UserCommands._

  def register(login: String, password: String, email: String): CommandResult[RegisterFailure, Long @@ User] = {
    logger.info(s"Registering user $login / $email")
    val escapedLogin = scala.xml.Utility.escape(login)

    if (RegisterDataValidator.isDataValid(escapedLogin, email, password)) {
      verifyUserDoesNotExist(escapedLogin, email).flatMap {
        case Left(error) => CommandResult.failed(UserExists(error))
        case p =>
          val salt = Utils.randomString(128)

          val eventData = UserRegistered(escapedLogin, email, User.encryptPassword(password, salt), salt)
          CommandResult.newAggregateId(Event(eventData).forNewAggregate)
      }
    }
    else {
      CommandResult.failed(ValidationFailed)
    }
  }

  private[user] def verifyUserDoesNotExist(userLogin: String, userEmail: String) = {
    val existingLoginFuture = userModel.findByLowerCasedLogin(userLogin)
    val existingEmailFuture = userModel.findByEmail(userEmail)

    for {
      existingLoginOpt <- existingLoginFuture.map(_.map(_ => "Login already in use!"))
      existingEmailOpt <- existingEmailFuture.map(_.map(_ => "E-mail already in use!"))
    } yield {
      existingLoginOpt.orElse(existingEmailOpt) match {
        case Some(error) => Left(error)
        case None => Right(())
      }
    }
  }

  def authenticate(login: String, plainPassword: String): CommandResult[Unit, User] = {
    userModel.findByLoginOrEmail(login).flatMap { userOptToCheck =>
      userOptToCheck.filter(u => User.passwordsMatch(plainPassword, u)) match {
        case Some(user) => CommandResult.successful(user, Event(UserLoggedIn()).forAggregate(user.id))
        case None => CommandResult.failed(())
      }
    }
  }
}

object UserCommands {
  sealed trait RegisterFailure
  case object ValidationFailed extends RegisterFailure
  case class UserExists(msg: String) extends RegisterFailure
}

object RegisterDataValidator {
  val MinLoginLength = 3

  def isDataValid(login: String, email: String, password: String): Boolean =
    validLogin(login.trim) &&
      validEmail(email.trim) &&
      validPassword(password.trim)

  private def validLogin(login: String) =
    login.length >= MinLoginLength

  private val emailRegex = """^[a-zA-Z0-9\.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$""".r

  private def validEmail(email: String) = emailRegex.findFirstMatchIn(email).isDefined

  private def validPassword(password: String) = !password.isEmpty
}