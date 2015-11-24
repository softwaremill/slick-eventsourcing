package com.softwaremill.example.database

import org.flywaydb.core.Flyway

object SchemaUpdate {
  def update(connectionString: String) {
    val flyway = new Flyway()
    flyway.setDataSource(connectionString, "", "")
    flyway.migrate()
  }
}
