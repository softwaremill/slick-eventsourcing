package com.softwaremill

import com.softwaremill.macwire.tagging._
import com.softwaremill.common.id.IdGenerator
import slick.dbio.Effect.{Write, Read}
import slick.dbio.{DBIO, DBIOAction, NoStream}

import scala.concurrent.ExecutionContext

package object events {
  type EventListener[T] = Event[T] => DBIOAction[List[PartialEvent[_, _]], NoStream, Read]
  type ModelUpdate[T] = Event[T] => DBIOAction[Unit, NoStream, Read with Write]
  /**
   * A command result consist of:
   *
   * * either a failure or success value (failure in case validation of the command input fails)
   * * a list of created events
   *
   * @tparam F Failure type
   * @tparam S Success type
   */
  type CommandResult[F, S] = DBIOAction[(Either[F, S], List[PartialEvent[_, _]]), NoStream, Read]

  object CommandResult {
    def successful[F, S](s: S, events: PartialEvent[_, _]*): CommandResult[F, S] = DBIO.successful((Right(s), events.toList))
    def failed[F, S](f: F, events: PartialEvent[_, _]*): CommandResult[F, S] = DBIO.successful((Left(f), events.toList))

    /**
     * A command result which:
     *
     * * contains an event creating a new aggregate
     * * returns the id of that aggregate
     */
    def newAggregateId[U, T, F](event: PartialEvent[U, T], idGenerator: IdGenerator): CommandResult[F, Long @@ U] = {
      val id = idGenerator.nextId().taggedWith[U]
      successful(id, event.copy(aggregateId = Some(id))(event.formats))
    }
  }

  implicit class CommandResultPimp[F, S](cr: CommandResult[F, S]) {
    def flatMapSuccess[F2 >: F, S2](f: S => CommandResult[F2, S2])(implicit ec: ExecutionContext): CommandResult[F2, S2] = cr.flatMap {
      case (Left(fail), events) => CommandResult.failed[F2, S2](fail, events: _*)
      case (Right(s), events) => f(s).flatMap {
        case (Left(fail2), events2) => CommandResult.failed(fail2, events ++ events2: _*)
        case (Right(s2), events2) => CommandResult.successful(s2, events ++ events2: _*)
      }
    }

    def addEvents(e: PartialEvent[_, _]*)(implicit ec: ExecutionContext): CommandResult[F, S] = cr.map {
      case (r, events) => (r, events ++ e.toList)
    }
  }
}
