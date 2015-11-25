package com.softwaremill.events

import org.flywaydb.core.Flyway
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}

trait SqlSpec extends BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures {
  this: Suite =>

  implicit val patience = PatienceConfig(timeout = Span(1000, Millis))

  private val connectionString = "jdbc:h2:mem:slickeventsourcing_test" + this.getClass.getSimpleName + ";DB_CLOSE_DELAY=-1"

  lazy val database = EventsDatabase.createH2(connectionString)

  override protected def beforeAll() {
    super.beforeAll()
    createAll()
  }

  override protected def afterAll() {
    super.afterAll()
    dropAll()
    database.close()
  }

  private def dropAll() {
    import database.driver.api._
    database.db.run(sqlu"DROP ALL OBJECTS").futureValue
  }

  private def createAll() {
    val flyway = new Flyway()
    flyway.setDataSource(connectionString, "", "")
    flyway.migrate()
  }

  override protected def afterEach() {
    try {
      dropAll()
      createAll()
    }
    catch {
      case e: Exception => e.printStackTrace()
    }

    super.afterEach()
  }
}
