package com.softwaremill.example.user

import com.softwaremill.test.{TestSqlData, BaseSqlSpec}

class UserCommandsSpec extends BaseSqlSpec with TestSqlData {

  val userCommands = new UserCommands(userModel, idGenerator)

  override protected def beforeEach() {
    super.beforeEach()

    createUser(login = "admin", email = "admin@sml.com")
  }

  "verifyUserDoesNotExist" should "not find given user login and e-mail" in {
    val r = userCommands.verifyUserDoesNotExist("newUser", "newUser@sml.com").run()
    r.isRight should be (true)
  }

  "verifyUserDoesNotExist" should "find duplicated login" in {
    val r = userCommands.verifyUserDoesNotExist("Admin", "newUser@sml.com").run()

    r.isLeft should be (true)
    r.left.get.equals("Login already in use!")
  }

  "verifyUserDoesNotExist" should "find duplicated login written as upper cased string" in {
    val r = userCommands.verifyUserDoesNotExist("ADMIN", "newUser@sml.com").run()

    r.isLeft should be (true)
    r.left.get.equals("Login already in use!")
  }

  "verifyUserDoesNotExist" should "find duplicated email" in {
    val r = userCommands.verifyUserDoesNotExist("newUser", "admin@sml.com").run()

    r.isLeft should be (true)
    r.left.get.equals("E-mail already in use!")
  }

  "verifyUserDoesNotExist" should "find duplicated email written as upper cased string" in {
    val r = userCommands.verifyUserDoesNotExist("newUser", "ADMIN@sml.com").run()

    r.isLeft should be (true)
    r.left.get.equals("E-mail already in use!")
  }
}
