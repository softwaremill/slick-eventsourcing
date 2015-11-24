package com.softwaremill.example.user

import java.time.OffsetDateTime

import com.softwaremill.database.SqlDatabase
import com.softwaremill.macwire.tagging._
import slick.dbio.Effect.{Write, Read}

import scala.concurrent.ExecutionContext

class UserModel(protected val database: SqlDatabase)(implicit val ec: ExecutionContext) extends SqlUserSchema {

  import database._
  import database.driver.api._

  def findById(userId: Long @@ User): DBIOAction[Option[User], NoStream, Read] =
    findOneWhere(_.id === userId)

  private def findOneWhere(condition: Users => Rep[Boolean]) =
    users.filter(condition).result.headOption

  def findByEmail(email: String): DBIOAction[Option[User], NoStream, Read] =
    findOneWhere(_.email.toLowerCase === email.toLowerCase)

  def findByLowerCasedLogin(login: String): DBIOAction[Option[User], NoStream, Read] =
    findOneWhere(_.loginLowerCase === login.toLowerCase)

  def findByLoginOrEmail(loginOrEmail: String): DBIOAction[Option[User], NoStream, Read] = {
    findByLowerCasedLogin(loginOrEmail).flatMap {
      case s @ Some(_) => DBIO.successful(s)
      case None => findByEmail(loginOrEmail)
    }
  }

  def updateNew(user: User): DBIOAction[Unit, NoStream, Write] = (users += user).map(_ => ())

  def updateLastLogin(userId: Long @@ User, lastLogin: OffsetDateTime): DBIOAction[Unit, NoStream, Write] = {
    users.filter(_.id === userId).map(_.lastLogin).update(Some(lastLogin)).map(_ => ())
  }
}

trait SqlUserSchema {

  protected val database: SqlDatabase

  import database._
  import database.driver.api._

  protected val users = TableQuery[Users]

  protected class Users(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long @@ User]("id", O.PrimaryKey)
    def login = column[String]("login")
    def loginLowerCase = column[String]("login_lowercase")
    def email = column[String]("email")
    def password = column[String]("password")
    def salt = column[String]("salt")
    def lastLogin = column[Option[OffsetDateTime]]("last_login")

    def * = (id, login, loginLowerCase, email, password, salt, lastLogin) <> ((User.apply _).tupled, User.unapply)
  }
}