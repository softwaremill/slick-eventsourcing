package com.softwaremill.example.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Route, AuthorizationFailedRejection}
import akka.http.scaladsl.server.Directives._
import com.softwaremill.events.HandleContext
import com.softwaremill.example.user.UserCommands.ValidationFailed
import com.softwaremill.example.user.{UserJson, UserCommands}
import com.softwaremill.session.SessionDirectives._
import com.typesafe.scalalogging.StrictLogging
import UserCommands._

trait UsersRoutes extends RoutesSupport with StrictLogging {

  def userCommands: UserCommands

  implicit val userJsonCbs = CanBeSerialized[UserJson]

  val usersRoutes = pathPrefix("users") {
    path("logout") {
      get {
        userIdFromSession { _ =>
          invalidateSession(oneOff, usingCookies) {
            completeOk
          }
        }
      }
    } ~
      path("register") {
        post {
          entity(as[RegisterInput]) { in =>
            implicit val hc = HandleContext.System

            cmdResult(userCommands.register(in.login, in.password, in.email)) { result =>
              onRegisterSuccessful(result) { userId =>
                setSession(oneOff, usingCookies, Session(userId)) {
                  complete("success")
                }
              }
            }
          }
        }
      } ~
      post {
        entity(as[LoginInput]) { in =>
          implicit val hc = HandleContext.System

          cmdResult(userCommands.authenticate(in.login, in.password)) {
            case Left(_) => reject(AuthorizationFailedRejection)
            case Right(user) =>
              val session = Session(user.id)
              setSession(oneOff, usingCookies, session) {
                complete(UserJson(user))
              }
          }
        }
      } ~
      get {
        userFromSession { user =>
          complete(UserJson(user))
        }
      }
  }
  def onRegisterSuccessful[T](result: Either[UserCommands.RegisterFailure, T])(success: T => Route): Route = result match {
    case Left(ValidationFailed) => complete(StatusCodes.BadRequest, "Wrong user data!")
    case Left(UserExists(msg)) => complete(StatusCodes.Conflict, msg)
    case Right(t) => success(t)
  }
}

case class RegisterInput(login: String, email: String, password: String)
case class LoginInput(login: String, password: String)
