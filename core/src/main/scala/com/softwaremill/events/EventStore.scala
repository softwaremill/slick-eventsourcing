package com.softwaremill.events

import java.time.OffsetDateTime

import com.typesafe.scalalogging.StrictLogging
import slick.dbio.Effect.Write

import scala.concurrent.ExecutionContext

class EventStore(protected val database: EventsDatabase)(implicit ec: ExecutionContext) extends SqlEventStoreSchema with StrictLogging {

  import database.driver.api._

  def store(event: StoredEvent): DBIOAction[Unit, NoStream, Write] = (events += event).map(_ => ())
}

trait SqlEventStoreSchema {
  protected val database: EventsDatabase

  import database._
  import database.driver.api._

  protected val events = TableQuery[Events]

  protected class Events(tag: Tag) extends Table[StoredEvent](tag, "events") {
    def id = column[Long]("id")
    def eventType = column[String]("event_type")
    def aggregateType = column[String]("aggregate_type")
    def aggregateId = column[Long]("aggregate_id")
    def aggregateIsNew = column[Boolean]("aggregate_is_new")
    def created = column[OffsetDateTime]("created")
    def userId = column[Long]("user_id")
    def txId = column[Long]("tx_id")
    def eventJson = column[String]("event_json")

    def * = (id, eventType, aggregateType, aggregateId, aggregateIsNew, created, userId, txId, eventJson) <> ((StoredEvent.apply _).tupled, StoredEvent.unapply)
  }
}

