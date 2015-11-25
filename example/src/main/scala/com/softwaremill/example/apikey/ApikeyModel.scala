package com.softwaremill.example.apikey

import java.time.OffsetDateTime

import com.softwaremill.events.EventsDatabase
import com.softwaremill.example.user.User
import com.softwaremill.tagging._
import slick.dbio.Effect.{Read, Write}

import scala.concurrent.ExecutionContext

class ApikeyModel(protected val database: EventsDatabase)(implicit ec: ExecutionContext) extends SqlApikeySchema {

  import database._
  import database.driver.api._

  def findByApikey(apikey: String): DBIOAction[Option[Apikey], NoStream, Read] =
    apikeys.filter(_.apikey === apikey).result.map(_.headOption)

  def findByUserId(userId: Long @@ User): DBIOAction[List[Apikey], NoStream, Read] =
    apikeys.filter(_.userId === userId).result.map(_.toList)

  def updateNew(apikey: Apikey): DBIOAction[Unit, NoStream, Write] =
    (apikeys += apikey).map(_ => ())

  def updateDelete(id: Long @@ Apikey): DBIOAction[Unit, NoStream, Write] =
    apikeys.filter(t => t.id === id).delete.map(_ => ())
}

case class Apikey(id: Long @@ Apikey, userId: Long @@ User, apikey: String, created: OffsetDateTime)

trait SqlApikeySchema {
  protected val database: EventsDatabase

  import database._
  import database.driver.api._

  protected val apikeys = TableQuery[Apikeys]

  protected class Apikeys(tag: Tag) extends Table[Apikey](tag, "apikeys") {
    def id = column[Long @@ Apikey]("id")
    def userId = column[Long @@ User]("user_id")
    def apikey = column[String]("apikey")
    def created = column[OffsetDateTime]("created")

    def * = (id, userId, apikey, created) <> ((Apikey.apply _).tupled, Apikey.unapply)
  }
}
