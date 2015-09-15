package com.softwaremill.example

import java.util.Locale

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.softwaremill.example.api.{Session, Routes}
import com.softwaremill.session.{SessionManager, SessionConfig}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Main() extends StrictLogging {
  def start(): (Future[ServerBinding], Beans) = {
    Locale.setDefault(Locale.US)

    implicit val _system = ActorSystem("slick-eventsourcing")
    implicit val _materializer = ActorMaterializer()
    import _system.dispatcher

    val modules = new Beans with Routes {
      lazy val sessionConfig = SessionConfig.fromConfig(config.rootConfig).withClientSessionEncryptData(true)

      implicit lazy val ec = _system.dispatcher
      implicit lazy val sessionManager: SessionManager[Session] = new SessionManager[Session](sessionConfig)
      implicit lazy val materializer = _materializer
      lazy val system = _system
    }

    modules.sqlDatabase.updateSchema()

    val startFuture = Http().bindAndHandle(modules.routes, modules.config.serverHost, modules.config.serverPort)

    (startFuture, modules)
  }
}

object Main extends App with StrictLogging {
  val (startFuture, modules) = new Main().start()

  val host = modules.config.serverHost
  val port = modules.config.serverPort

  val system = modules.system
  import system.dispatcher

  startFuture.onComplete {
    case Success(b) =>
      logger.info(s"Server started on $host:$port")
      sys.addShutdownHook {
        b.unbind()
        system.shutdown()
        logger.info("Server stopped")
      }
    case Failure(e) =>
      logger.error(s"Cannot start server on $host:$port", e)
      sys.addShutdownHook {
        system.shutdown()
        logger.info("Server stopped")
      }
  }
}
