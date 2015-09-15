package com.softwaremill.example.user

import com.softwaremill.events.{HandleContext, Registry}
import com.softwaremill.example.apikey.{ApikeyCommands, ApikeyCreated, ApikeyModel}
import com.softwaremill.example.email.EmailService
import com.softwaremill.example.user.UserCommands.UserExists
import com.softwaremill.test.{BaseSqlSpec, EventSink, TestEventMachineModule, TestSqlData}

class UserModuleSpec extends BaseSqlSpec with TestSqlData { spec =>

  def createModules = new UserModule with TestEventMachineModule {
    lazy val emailService = new EmailService {
      var emails: List[(String, String)] = Nil
      override def sendEmail(to: String, content: String) = {
        emails = (to, content) :: emails
        super.sendEmail(to, content)
      }
    }
    lazy val apikeyCommands = new ApikeyCommands(new ApikeyModel(sqlDatabase))

    lazy val apikeyCreatedEventSink = new EventSink[ApikeyCreated]

    override lazy val registry = addUserListeners(Registry())
      .registerEventListener(apikeyCreatedEventSink)
    override lazy val sqlDatabase = spec.database
  }

  it should "register a new user" in {
    // Given
    val m = createModules
    implicit val ctx = HandleContext.System

    // When
    val res = m.runCommand(m.userCommands.register("John", "password", "newUser@sml.com"))

    // Then
    res should be ('right)

    val userOptByLogin = m.userModel.findByLowerCasedLogin("John").run()
    val userOptById = m.userModel.findById(res.right.get).run()
    userOptByLogin.isDefined should be (true)
    userOptById.isDefined should be (true)
    userOptByLogin should be (userOptById)

    val Some(user) = userOptByLogin

    user.login should be ("John")
    user.loginLowerCased should be ("john")
    m.emailService.emails.headOption.map(_._1) should be (Some("newUser@sml.com"))

    m.apikeyCreatedEventSink.events should have length (1)
  }

  it should "not register on an existing login" in {
    // Given
    val m = createModules
    implicit val ctx = HandleContext.System

    // When
    val res1 = m.runCommand(m.userCommands.register("Jack", "password", "john@sml.com"))
    val res2 = m.runCommand(m.userCommands.register("jAck", "password", "jack@sml.com"))

    // Then
    res1 should be ('right)
    res2 should matchPattern { case Left(UserExists(_)) => }

    m.emailService.emails.headOption.map(_._1) should be (Some("john@sml.com"))
  }

  it should "authenticate a user" in {
    // Given
    val m = createModules
    implicit val hc = HandleContext.System
    createUser(login = "john", password = "password")

    // When
    val res1 = m.runCommand(m.userCommands.authenticate("john", "password"))
    val res2 = m.runCommand(m.userCommands.authenticate("john", "HACKER"))

    // Then
    res1 should matchPattern { case Right(_) => }
    res2 should be (Left(()))
  }
}
