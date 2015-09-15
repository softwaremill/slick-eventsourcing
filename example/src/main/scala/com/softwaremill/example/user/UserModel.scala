package com.softwaremill.example.user

import com.softwaremill.database.{DBWrite, DBRead, SqlDatabase}
import com.softwaremill.macwire._
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext

class UserModel(protected val database: SqlDatabase)(implicit val ec: ExecutionContext) extends SqlUserSchema {

  import database._
  import database.driver.api._

  def findById(userId: Long @@ User): DBRead[Option[User]] =
    findOneWhere(_.id === userId)

  private def findOneWhere(condition: Users => Rep[Boolean]) =
    users.filter(condition).result.headOption

  def findByEmail(email: String): DBRead[Option[User]] =
    findOneWhere(_.email.toLowerCase === email.toLowerCase)

  def findByLowerCasedLogin(login: String): DBRead[Option[User]] =
    findOneWhere(_.loginLowerCase === login.toLowerCase)

  def findByLoginOrEmail(loginOrEmail: String): DBRead[Option[User]] = {
    findByLowerCasedLogin(loginOrEmail).flatMap {
      case s @ Some(_) => DBIO.successful(s)
      case None => findByEmail(loginOrEmail)
    }
  }

  def updateNew(user: User): DBWrite = (users += user).map(_ => ())

  def updateLastLogin(userId: Long @@ User, lastLogin: DateTime): DBWrite = {
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
    def lastLogin = column[Option[DateTime]]("last_login")

    def * = (id, login, loginLowerCase, email, password, salt, lastLogin) <> ((User.apply _).tupled, User.unapply)
  }
}