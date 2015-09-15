package com.softwaremill.database

import com.softwaremill.macwire._
import com.typesafe.scalalogging.StrictLogging
import org.flywaydb.core.Flyway
import org.joda.time.{DateTimeZone, DateTime}
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

case class SqlDatabase(
    db: slick.jdbc.JdbcBackend.Database,
    driver: JdbcProfile,
    connectionString: JdbcConnectionString
) {

  import driver.api._

  implicit val dateTimeColumnType = MappedColumnType.base[DateTime, java.sql.Timestamp](
    dt => new java.sql.Timestamp(dt.getMillis),
    t => new DateTime(t.getTime).withZone(DateTimeZone.UTC)
  )

  implicit def taggedIdColumnType[U] = MappedColumnType.base[Long @@ U, Long](
    { _.asInstanceOf[Long] },
    { _.taggedWith[U] }
  )

  def updateSchema() {
    val flyway = new Flyway()
    flyway.setDataSource(connectionString.url, connectionString.username, connectionString.password)
    flyway.migrate()
  }

  def close() {
    db.close()
  }
}

case class JdbcConnectionString(url: String, username: String = "", password: String = "")

object SqlDatabase extends StrictLogging {
  def createEmbedded(connectionString: String): SqlDatabase = {
    val db = Database.forURL(connectionString)
    SqlDatabase(db, slick.driver.H2Driver, JdbcConnectionString(connectionString))
  }
}
