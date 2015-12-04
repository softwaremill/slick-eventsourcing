package com.softwaremill.events

import java.time.{OffsetDateTime, ZoneOffset}

import com.softwaremill.tagging._
import slick.driver.JdbcProfile
import slick.jdbc.JdbcBackend._

class EventsDatabase(val db: slick.jdbc.JdbcBackend#Database, val driver: JdbcProfile) {

  import driver.api._

  implicit val offsetDateTimeColumnType = MappedColumnType.base[OffsetDateTime, java.sql.Timestamp](
    dt => new java.sql.Timestamp(dt.toInstant.toEpochMilli),
    t => t.toInstant.atOffset(ZoneOffset.UTC)
  )

  implicit def taggedIdColumnType[U] = MappedColumnType.base[Long @@ U, Long](
    { _.asInstanceOf[Long] },
    { _.taggedWith[U] }
  )

  def close() {
    db.close()
  }
}

object EventsDatabase {
  def createH2(connectionString: String): EventsDatabase = {
    val db = Database.forURL(connectionString)
    new EventsDatabase(db, slick.driver.H2Driver)
  }
}
