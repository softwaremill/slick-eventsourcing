package com.softwaremill.database

import java.time.{OffsetDateTime, ZoneOffset}

import com.softwaremill.macwire.tagging._
import com.typesafe.scalalogging.StrictLogging
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

case class SqlDatabase(
    db: slick.jdbc.JdbcBackend.Database,
    driver: JdbcProfile
) {

  import driver.api._

  implicit val offsetDateTimeColumnType = MappedColumnType.base[OffsetDateTime, java.sql.Timestamp](
    dt => new java.sql.Timestamp(dt.toInstant.toEpochMilli),
    t => t.toLocalDateTime.atOffset(ZoneOffset.UTC)
  )

  implicit def taggedIdColumnType[U] = MappedColumnType.base[Long @@ U, Long](
    { _.asInstanceOf[Long] },
    { _.taggedWith[U] }
  )

  def close() {
    db.close()
  }
}

object SqlDatabase extends StrictLogging {
  def createEmbedded(connectionString: String): SqlDatabase = {
    val db = Database.forURL(connectionString)
    SqlDatabase(db, slick.driver.H2Driver)
  }
}
