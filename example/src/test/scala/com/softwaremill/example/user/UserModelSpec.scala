package com.softwaremill.example.user

import com.softwaremill.macwire._
import com.softwaremill.test.{TestSqlData, BaseSqlSpec}

import scala.language.implicitConversions

class UserModelSpec extends BaseSqlSpec with TestSqlData {

  lazy val randomIds: List[Long @@ User] = List.fill(3)(idGenerator.nextId().taggedWith[User])

  override def beforeEach() {
    super.beforeEach()

    for (i <- 1 to randomIds.size) {
      val login = "user" + i
      val password = "pass" + i
      val salt = "salt" + i
      val token = "token" + i
      userModel.updateNew(User(randomIds(i - 1), login, login.toLowerCase, i + "email@sml.com", password, salt, None)).run()
    }
  }

  it should "add new user" in {
    // Given
    val login = "newuser"
    val email = "newemail@sml.com"

    // When
    userModel.updateNew(User(idGenerator.nextId().taggedWith[User], login, login, email, "pass", "salt", None)).run()

    // Then
    userModel.findByEmail(email).run() should be ('defined)
  }

  it should "find by email" in {
    // Given
    val email = "1email@sml.com"

    // When
    val userOpt = userModel.findByEmail(email).run()

    // Then
    userOpt.map(_.email) should equal(Some(email))
  }

  it should "find by uppercase email" in {
    // Given
    val email = "1email@sml.com".toUpperCase

    // When
    val userOpt = userModel.findByEmail(email).run()

    // Then
    userOpt.map(_.email) should equal(Some(email.toLowerCase))
  }

  it should "find by login" in {
    // Given
    val login = "user1"

    // When
    val userOpt = userModel.findByLowerCasedLogin(login).run()

    // Then
    userOpt.map(_.login) should equal(Some(login))
  }

  it should "find by uppercase login" in {
    // Given
    val login = "user1".toUpperCase

    // When
    val userOpt = userModel.findByLowerCasedLogin(login).run()

    // Then
    userOpt.map(_.login) should equal(Some(login.toLowerCase))
  }

  it should "find using login with findByLoginOrEmail" in {
    // Given
    val login = "user1"

    // When
    val userOpt = userModel.findByLoginOrEmail(login).run()

    // Then
    userOpt.map(_.login) should equal(Some(login.toLowerCase))
  }

  it should "find using uppercase login with findByLoginOrEmail" in {
    // Given
    val login = "user1".toUpperCase

    // When
    val userOpt = userModel.findByLoginOrEmail(login).run()

    // Then
    userOpt.map(_.login) should equal(Some(login.toLowerCase))
  }

  it should "find using email with findByLoginOrEmail" in {
    // Given
    val email = "1email@sml.com"

    // When
    val userOpt = userModel.findByLoginOrEmail(email).run()

    // Then
    userOpt.map(_.email) should equal(Some(email.toLowerCase))
  }

  it should "find using uppercase email with findByLoginOrEmail" in {
    // Given
    val email = "1email@sml.com".toUpperCase

    // When
    val userOpt = userModel.findByLoginOrEmail(email).run()

    // Then
    userOpt.map(_.email) should equal(Some(email.toLowerCase))
  }
}
