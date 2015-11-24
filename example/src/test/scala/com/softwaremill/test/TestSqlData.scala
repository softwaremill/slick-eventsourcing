package com.softwaremill.test

import com.softwaremill.example.common.Utils
import com.softwaremill.macwire.tagging._
import com.softwaremill.example.user.{UserModel, User}
import slick.dbio.{DBIOAction, NoStream}

trait TestSqlData {
  this: BaseSqlSpec =>

  lazy val userModel = new UserModel(database)

  def createUser(
    login: String = Utils.randomString(16),
    password: String = Utils.randomString(16),
    email: String = s"${Utils.randomString(8)}@${Utils.randomString(8)}.com"
  ): User = {

    val id = idGenerator.nextId()
    val salt = Utils.randomString(16)
    val user = User(id.taggedWith[User], login, login.toLowerCase, email, User.encryptPassword(password, salt), salt, None)
    userModel.updateNew(user).run()
    user
  }

  implicit class RunDbAction[R](action: DBIOAction[R, NoStream, Nothing]) {
    def run(): R = database.db.run(action).futureValue
  }
}
