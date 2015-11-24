package com.softwaremill

import com.softwaremill.macwire.tagging._
import com.softwaremill.common.id.IdGenerator
import com.softwaremill.database.{DBReadWrite, DBRead}
import slick.dbio.Effect.Read
import slick.dbio.{DBIO, DBIOAction, NoStream}

import scala.concurrent.ExecutionContext

package object events {
  type CommandResult[F, S] = DBIOAction[(Either[F, S], List[PartialEvent[_, _]]), NoStream, Read]

  object CommandResult {
    def successful[F, S](s: S, events: PartialEvent[_, _]*): CommandResult[F, S] = DBIO.successful((Right(s), events.toList))
    def failed[F, S](f: F, events: PartialEvent[_, _]*): CommandResult[F, S] = DBIO.successful((Left(f), events.toList))

    def newAggregateId[U, T, F](event: PartialEvent[U, T])(implicit idGenerator: IdGenerator): CommandResult[F, Long @@ U] = {
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

  type EventListener[T] = Event[T] => DBRead[List[PartialEvent[_, _]]]
  type ModelUpdate[T] = Event[T] => DBReadWrite
}
