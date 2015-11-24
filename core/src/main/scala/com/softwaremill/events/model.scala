package com.softwaremill.events

import java.time.{ZoneOffset, Clock, OffsetDateTime}

import com.softwaremill.common.id.IdGenerator
import com.softwaremill.macwire.tagging._
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.Serialization

import scala.reflect.ClassTag

/**
 * For each event, provide an implicit value of type `AggregateForEvent` which specifies with which aggregate is
 * this event associated. This is used e.g. for id type-tagging.
 *
 * If the implicit is defined in the companion object for the event, it will be found automatically by the compiler.
 *
 * @tparam T Event type
 * @tparam U Aggregate type
 */
trait AggregateForEvent[T, U]
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
   * Main entry point for creating new events.
   */
  def apply[U: ClassTag, T <: Product](data: T)(implicit afe: AggregateForEvent[T, U], formats: Formats = DefaultFormats): EventForAggregateBuilder[U, T] =
    EventForAggregateBuilder(data, formats, afe)
}

case class EventForAggregateBuilder[U: ClassTag, T <: Product](data: T, formats: Formats, afe: AggregateForEvent[T, U]) {
  def forNewAggregate: PartialEvent[U, T] =
    PartialEvent(None, aggregateIsNew = true, data, formats)

  def forAggregate(aggregateId: Long @@ U): PartialEvent[U, T] =
    PartialEvent(Some(aggregateId), aggregateIsNew = false, data, formats)

  def forAggregate(aggregateId: Option[Long @@ U]): PartialEvent[U, T] = aggregateId match {
    case None => forNewAggregate
    case Some(id) => forAggregate(id)
  }

  def forNewAggregateWithId(aggregateId: Long @@ U): PartialEvent[U, T] =
    PartialEvent(Some(aggregateId), aggregateIsNew = true, data, formats)
}

case class PartialEvent[U, T](eventType: String, aggregateType: String, aggregateId: Option[Long @@ U], aggregateIsNew: Boolean,
    data: T)(val formats: Formats) {
  def withIds(implicit idGenerator: IdGenerator, clock: Clock) =
    PartialEventWithId(idGenerator.nextId(), eventType, aggregateType,
      aggregateId.getOrElse(idGenerator.nextId().taggedWith[U]),
      aggregateIsNew, clock.instant().atOffset(ZoneOffset.UTC), data)(formats)
}

object PartialEvent {
  def apply[U: ClassTag, T <: Product](aggregateId: Option[Long @@ U], aggregateIsNew: Boolean,
    data: T, formats: Formats): PartialEvent[U, T] =
    PartialEvent[U, T](data.productPrefix, implicitly[ClassTag[U]].runtimeClass.getSimpleName, aggregateId, aggregateIsNew,
      data)(formats)
}

case class PartialEventWithId[U, T](id: Long, eventType: String, aggregateType: String, aggregateId: Long @@ U, aggregateIsNew: Boolean,
    created: OffsetDateTime, data: T)(val formats: Formats) {
  def toEvent(rawUserId: Long, txId: Long) =
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

trait HandleContextTransform[U] {
  def apply(e: PartialEventWithId[U, _], hc: HandleContext): HandleContext
}

/**
 * A way to parametrise a subproject with a type. There should be exactly one implicit value of this type, parametrised
 * by the type of the "user" entity. This is needed to properly tag the user id in events.
 */
trait UserType[-User]