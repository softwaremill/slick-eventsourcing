package com.softwaremill.example.apikey

import com.softwaremill.database.{DBWrite, DBRead, SqlDatabase}
import com.softwaremill.macwire.tagging._
import com.softwaremill.example.user.User
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext

class ApikeyModel(protected val database: SqlDatabase)(implicit ec: ExecutionContext) extends SqlApikeySchema {

  import database._
  import database.driver.api._

  def findByApikey(apikey: String): DBRead[Option[Apikey]] =
    apikeys.filter(_.apikey === apikey).result.map(_.headOption)

  def findByUserId(userId: Long @@ User): DBRead[List[Apikey]] =
    apikeys.filter(_.userId === userId).result.map(_.toList)

  def updateNew(apikey: Apikey): DBWrite =
    (apikeys += apikey).map(_ => ())

  def updateDelete(id: Long @@ Apikey): DBWrite =
    apikeys.filter(t => t.id === id).delete.map(_ => ())
}

case class Apikey(id: Long @@ Apikey, userId: Long @@ User, apikey: String, created: DateTime)

trait SqlApikeySchema {
  protected val database: SqlDatabase

  import database._
  import database.driver.api._

  protected val apikeys = TableQuery[Apikeys]

  protected class Apikeys(tag: Tag) extends Table[Apikey](tag, "apikeys") {
    def id = column[Long @@ Apikey]("id")
    def userId = column[Long @@ User]("user_id")
    def apikey = column[String]("apikey")
    def created = column[DateTime]("created")

    def * = (id, userId, apikey, created) <> ((Apikey.apply _).tupled, Apikey.unapply)
  }
}
