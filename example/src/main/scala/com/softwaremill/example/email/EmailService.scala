package com.softwaremill.example.email

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future

class EmailService extends StrictLogging {
  def sendEmail(to: String, content: String): Future[Unit] = {
    logger.info(s"Sending email $content to $to")
    Future.successful(())
  }
}
