package com.softwaremill.example

import java.time.Clock

import akka.actor.ActorSystem
import com.softwaremill.database.{DatabaseConfig, SqlDatabase}
import com.softwaremill.events.{EventsModule, EventMachine, EventStore, Registry}
import com.softwaremill.example.apikey.ApikeyModule
import com.softwaremill.common.id.IdGenerator
import com.softwaremill.example.email.EmailService
import com.softwaremill.example.user.UserModule
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

trait Beans extends StrictLogging
    with UserModule
    with ApikeyModule
    with EventsModule {

  lazy val config = new DatabaseConfig with ServerConfig {
    override def rootConfig = ConfigFactory.load()
  }

  override lazy val sqlDatabase = SqlDatabase.createEmbedded(config.dbH2Url)

  implicit val clock: Clock = Clock.systemUTC()
  implicit val idGenerator = new IdGenerator(datacenterId = 1)

  lazy val emailService = new EmailService()

  lazy val registry = addUserListeners
    .andThen(addApikeyListeners)
    .apply(Registry())

  def system: ActorSystem
}
