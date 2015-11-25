package com.softwaremill.events

import java.time.{ZoneOffset, Clock, OffsetDateTime}

import com.softwaremill.id.IdGenerator
import com.softwaremill.tagging._
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.Serialization

import scala.reflect.ClassTag

/**
  * For each event, provide an implicit value of type `AggregateForEvent` which specifies with which aggregate is
  * this event associated. This is used e.g. for id type-tagging.
  *
  * If the companion object contains the implicit or extends `AggregateForEvent` with the correct type parameters,
  * the implicit will be found automatically by the compiler, without the need for additional imports.
  *
  * @tparam T Event type
  * @tparam U Aggregate type
  */
trait AggregateForEvent[T, U] {
  implicit val afe: AggregateForEvent[T, U] = null
}
object AggregateForEvent {
  def apply[T, U]: AggregateForEvent[T, U] = null
}

case class Event[T](id: Long, eventType: String, aggregateType: String, rawAggregateId: Long, aggregateIsNew: Boolean,
    created: OffsetDateTime, rawUserId: Long, txId: Long, data: T)(implicit formats: Formats) {

  def aggregateId[U](implicit afe: AggregateForEvent[T, U]): Long @@ U = rawAggregateId.taggedWith[U]

  def userId[User](implicit ut: UserType[User]): Long @@ User = rawUserId.taggedWith[User]

  def toStoredEvent = {
    StoredEvent(id, eventType, aggregateType, rawAggregateId, aggregateIsNew, created, rawUserId, txId,
      Serialization.write(data.asInstanceOf[AnyRef]))
  }
}

object Event {
  /**
    * Main entry point for creating new events. Creates a builder for a new event with the given event data.
    *
    * @param formats If the event contains field which require custom serializers, provide the json formats needed
    *                to serialize it.
    */
  def apply[U: ClassTag, T <: Product](data: T)(implicit afe: AggregateForEvent[T, U], formats: Formats = DefaultFormats): EventForAggregateBuilder[U, T] =
    EventForAggregateBuilder(data, formats, afe)
}

case class EventForAggregateBuilder[U: ClassTag, T <: Product](data: T, formats: Formats, afe: AggregateForEvent[T, U]) {
  /**
    * This event creates a new aggregate. An id will be generated when the events are processed.
    */
  def forNewAggregate: PartialEvent[U, T] =
    PartialEvent(None, aggregateIsNew = true, data, formats)

  /**
    * This event modifies an existing aggregate.
    */
  def forAggregate(aggregateId: Long @@ U): PartialEvent[U, T] =
    PartialEvent(Some(aggregateId), aggregateIsNew = false, data, formats)

  /**
    * This events creates a new, or modifies an existing aggregate.
    */
  def forAggregate(aggregateId: Option[Long @@ U]): PartialEvent[U, T] = aggregateId match {
    case None => forNewAggregate
    case Some(id) => forAggregate(id)
  }

  /**
    * This event creates a new aggregate. The id (`aggregateId`) is specified upfront.
    */
  def forNewAggregateWithId(aggregateId: Long @@ U): PartialEvent[U, T] =
    PartialEvent(Some(aggregateId), aggregateIsNew = true, data, formats)
}

case class PartialEvent[U, T](eventType: String, aggregateType: String, aggregateId: Option[Long @@ U], aggregateIsNew: Boolean,
    data: T)(val formats: Formats) {
  private[events] def withIds(idGenerator: IdGenerator, clock: Clock) =
    PartialEventWithId(idGenerator.nextId(), eventType, aggregateType,
      aggregateId.getOrElse(idGenerator.nextId().taggedWith[U]),
      aggregateIsNew, clock.instant().atOffset(ZoneOffset.UTC), data)(formats)
}

object PartialEvent {
  private[events] def apply[U: ClassTag, T <: Product](aggregateId: Option[Long @@ U], aggregateIsNew: Boolean,
    data: T, formats: Formats): PartialEvent[U, T] =
    PartialEvent[U, T](data.productPrefix, implicitly[ClassTag[U]].runtimeClass.getSimpleName, aggregateId, aggregateIsNew,
      data)(formats)
}

case class PartialEventWithId[U, T](id: Long, eventType: String, aggregateType: String, aggregateId: Long @@ U, aggregateIsNew: Boolean,
    created: OffsetDateTime, data: T)(val formats: Formats) {
  private[events] def toEvent(rawUserId: Long, txId: Long) =
    Event[T](id, eventType, aggregateType, aggregateId, aggregateIsNew, created, rawUserId, txId, data)(formats)
}

case class StoredEvent(id: Long, eventType: String, aggregateType: String, aggregateId: Long, aggregateIsNew: Boolean,
  created: OffsetDateTime, userId: Long, txId: Long, eventJson: String)

/**
  * Context in which events are created and handled: for example, the currently logged in user id.
  */
class HandleContext private[events] (private[events] val rawUserId: Long) {
  def withUserId[User](userId: Long @@ User)(implicit ut: UserType[User]) = new HandleContext(userId)
}

object HandleContext {
  def apply[User](userId: Long @@ User)(implicit ut: UserType[User]) = new HandleContext(userId)
  val System = new HandleContext(-1L)
}

/**
  * Should be implemented by events which change the "current" user, e.g. a user-registered or user-logged-in events.
  */
trait HandleContextTransform[U] {
  def apply(e: PartialEventWithId[U, _], hc: HandleContext): HandleContext
}

/**
  * A way to parametrise a whole project with a type. There should be exactly one implicit value of this type,
  * parametrised by the type of the "user" aggregate (entity). This is needed to properly tag the user id in events.
  */
trait UserType[-User]