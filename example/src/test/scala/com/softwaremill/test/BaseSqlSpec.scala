package com.softwaremill.test

import com.softwaremill.database.SqlDatabase
import com.softwaremill.example.database.SchemaUpdate
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

trait BaseSqlSpec extends BaseSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  private val connectionString = "jdbc:h2:mem:slickeventsourcing_test" + this.getClass.getSimpleName + ";DB_CLOSE_DELAY=-1"

  lazy val database = SqlDatabase.createH2(connectionString)

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
    SchemaUpdate.update(connectionString)
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
