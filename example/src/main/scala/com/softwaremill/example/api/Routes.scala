package com.softwaremill.example.api

import akka.http.scaladsl.model.headers.CacheDirectives.{`max-age`, `must-revalidate`, `no-store`, `no-cache`, `public`}
import akka.http.scaladsl.model.{DateTime, StatusCodes}
import akka.http.scaladsl.model.headers.{`Cache-Control`, Expires, `Last-Modified`}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import org.json4s.MappingException

trait Routes extends ApikeyRoutes with UsersRoutes {

  private val exceptionHandler = ExceptionHandler {
    case e: MappingException =>
      logger.error(s"Incorrect JSON received: ${e.getMessage}", e)
      _.complete(StatusCodes.BadRequest, e.getMessage)
    case e: Exception =>
      logger.error(s"Exception during client request processing: ${e.getMessage}", e)
      _.complete(StatusCodes.InternalServerError, "Internal server error")
  }

  val routes =
    handleExceptions(exceptionHandler) {
      encodeResponse {
        pathPrefix("api") {
          apikeyRoutes ~
            usersRoutes
        } ~
          getFromResourceDirectory("webapp") ~
          // any other path -> returning index, will be handled by the frontend
          pathPrefixTest(!("api" | "oauth")) {
            getFromResource("webapp/index.html")
          }
      }
    }
}
