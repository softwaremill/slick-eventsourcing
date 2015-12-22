package com.softwaremill.example.api

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1, Route}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import com.softwaremill.events.{EventsDatabase, CommandResult, EventMachine, HandleContext}
import com.softwaremill.example.user.{User, UserModel}
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session.SessionManager
import com.softwaremill.tagging._
import org.json4s.JsonAST.JString
import org.json4s._
import slick.dbio.{DBIOAction, Effect, NoStream}

import scala.concurrent.ExecutionContext

trait RoutesSupport extends JsonSupport with SessionSupport with DatabaseSupport {
  def completeOk = complete("ok")
  def completeEmpty = complete("")
}

trait JsonSupport {
  protected val dateTimeFormat = DateTimeFormatter.ISO_DATE_TIME
  protected val dateTimeSerializer = new CustomSerializer[OffsetDateTime](formats => ({
    case JString(s) => OffsetDateTime.parse(s, dateTimeFormat)
  }, {
    case d: OffsetDateTime => JString(dateTimeFormat.format(d))
  }))

  protected implicit def jsonFormats: Formats = DefaultFormats + dateTimeSerializer

  implicit val serialization = native.Serialization
  implicit def materializer: Materializer

  // from https://github.com/hseeberger/akka-http-json/blob/master/akka-http-json4s/src/main/scala/de/heikoseeberger/akkahttpjson4s/Json4sSupport.scala
  implicit def json4sUnmarshaller[A <: Product: Manifest]: FromEntityUnmarshaller[A] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .mapWithCharset { (data, charset) =>
        val input = if (charset == HttpCharsets.`UTF-8`) data.utf8String else data.decodeString(charset.nioCharset.name)
        serialization.read(input)
      }

  private def transformLongIdsToStrIds(jv: JValue): JValue = jv.transformField {
    case JField(name, JInt(num)) => JField(name + "Str", JString(num.toString()))
  }

  implicit def json4sMarshaller[A <: AnyRef](implicit cbs: CanBeSerialized[A]): ToEntityMarshaller[A] = {
    import native.JsonMethods._
    Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`) {
      (Extraction.decompose _)
        .andThen(transformLongIdsToStrIds)
        .andThen(render)
        .andThen(compact)
    }
  }

  trait CanBeSerialized[T]
  object CanBeSerialized {
    def apply[T] = new CanBeSerialized[T] {}
    implicit def listCanBeSerialized[T](implicit cbs: CanBeSerialized[T]): CanBeSerialized[List[T]] = null
    implicit def setCanBeSerialized[T](implicit cbs: CanBeSerialized[T]): CanBeSerialized[Set[T]] = null
    implicit def optionCanBeSerialized[T](implicit cbs: CanBeSerialized[T]): CanBeSerialized[Option[T]] = null
  }
}

trait SessionSupport {
  this: DatabaseSupport =>

  implicit def sessionManager: SessionManager[Session]
  implicit def ec: ExecutionContext

  def userModel: UserModel
  def eventMachine: EventMachine

  def userFromSession: Directive1[User] = userIdFromSession.flatMap { userId =>
    dbResult(userModel.findById(userId)).flatMap {
      case None => reject(AuthorizationFailedRejection)
      case Some(user) => provide(user)
    }
  }

  def userIdFromSession: Directive1[Long @@ User] = requiredSession(oneOff, usingCookies).map(_.userId)
}

trait DatabaseSupport {
  def eventsDatabase: EventsDatabase
  def eventMachine: EventMachine

  def dbResult[R](action: DBIOAction[R, NoStream, Nothing]): Directive1[R] = {
    onSuccess(eventsDatabase.db.run(action))
  }

  def cmdResult[F, S](cr: CommandResult[F, S])(body: Either[F, S] => Route)(implicit hc: HandleContext): Route = {
    onSuccess(eventMachine.run(cr))(body)
  }

  implicit def dbioActionMarshaller[R, S <: NoStream, E <: Effect](implicit rMarshaller: ToEntityMarshaller[R]): ToEntityMarshaller[DBIOAction[R, S, E]] = {
    Marshaller { implicit ec => (value: DBIOAction[R, S, E]) =>
      eventsDatabase.db.run(value).flatMap(r => rMarshaller.apply(r))
    }
  }
}