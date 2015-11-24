package com.softwaremill.example

import akka.actor.ActorSystem
import com.softwaremill.common.id.DefaultIdGenerator
import com.softwaremill.database.SqlDatabase
import com.softwaremill.events.{EventsModule, Registry}
import com.softwaremill.example.apikey.ApikeyModule
import com.softwaremill.example.database.DatabaseConfig
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

  override lazy val sqlDatabase = SqlDatabase.createH2(config.dbH2Url)

  lazy val idGenerator = new DefaultIdGenerator(datacenterId = 1)

  lazy val emailService = new EmailService()

  lazy val registry = addUserListeners
    .andThen(addApikeyListeners)
    .apply(Registry())

  def system: ActorSystem
}
