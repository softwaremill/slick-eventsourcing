package com.softwaremill.example.database

import com.typesafe.config.Config

trait DatabaseConfig {
  def rootConfig: Config

  lazy val dbH2Url = rootConfig.getString("db.h2.properties.url")
}
