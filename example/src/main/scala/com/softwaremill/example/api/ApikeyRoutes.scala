package com.softwaremill.example.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import com.softwaremill.events.HandleContext
import com.softwaremill.example.apikey.{Apikey, ApikeyModel, ApikeyCommands}

trait ApikeyRoutes extends RoutesSupport {

  def apikeyCommands: ApikeyCommands
  def apikeyModel: ApikeyModel

  implicit val apikeyCbs = CanBeSerialized[Apikey]

  val apikeyRoutes = pathPrefix("apikey") {
    userIdFromSession { userId =>
      implicit val hc = HandleContext(userId)
      path("single") {
        get {
          dbResult(apikeyModel.findByUserId(userId).map(_.sortBy(-_.created.getMillis).headOption)) {
            case Some(key) => complete(key)
            case None => completeEmpty
          }
        }
      } ~
        path("delete") {
          post {
            entity(as[ApikeyInput]) { in =>
              cmdResult(apikeyCommands.delete(in.apikey)) { _ =>
                complete {
                  apikeyModel.findByUserId(userId)
                }
              }
            }
          }
        } ~
        get {
          complete {
            apikeyModel.findByUserId(userId)
          }
        } ~
        post {
          cmdResult(apikeyCommands.create()) { _ =>
            complete {
              apikeyModel.findByUserId(userId)
            }
          }
        }
    }
  }
}

case class ApikeyInput(apikey: String)