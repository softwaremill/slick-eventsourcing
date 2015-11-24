package com.softwaremill.database

import com.typesafe.config.Config

trait DatabaseConfig {
  def rootConfig: Config

  lazy val dbH2Url = rootConfig.getString("db.h2.properties.url")
}
