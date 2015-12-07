package com.softwaremill.example

import java.util.Locale

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.softwaremill.example.api.{Routes, Session}
import com.softwaremill.example.database.SchemaUpdate
import com.softwaremill.session.{SessionConfig, SessionManager}

object RecoverDbState extends App {
  Locale.setDefault(Locale.US)

  implicit val _system = ActorSystem("slick-eventsourcing")
  implicit val _materializer = ActorMaterializer()

  val modules = new Beans with Routes {
    lazy val sessionConfig = SessionConfig.fromConfig(config.rootConfig).copy(sessionEncryptData = true)

    implicit lazy val ec = _system.dispatcher
    implicit lazy val sessionManager: SessionManager[Session] = new SessionManager[Session](sessionConfig)
    implicit lazy val materializer = _materializer
    lazy val system = _system
  }

  SchemaUpdate.update(modules.config.dbH2Url)
  private val handledEvents = modules.eventMachine.failOverFromStoredEventsTo()

}
