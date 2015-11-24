package com.softwaremill.events

import java.time.Clock

import com.softwaremill.common.id.IdGenerator
import com.softwaremill.database.SqlDatabase
import com.typesafe.scalalogging.StrictLogging
import slick.dbio.Effect.{Transactional, Read, Write}
import slick.dbio.{DBIO, DBIOAction, NoStream}

import scala.concurrent.{Future, ExecutionContext}

class EventMachine(database: SqlDatabase, registry: Registry, eventStore: EventStore, asyncEventScheduler: AsyncEventScheduler)(
    implicit
    ec: ExecutionContext, idGenerator: IdGenerator, clock: Clock
) extends StrictLogging {

  def run[F, S](cr: CommandResult[F, S])(implicit hc: HandleContext): Future[Either[F, S]] = {
    database.db.run(handle(cr))
  }

  def handle[F, S](cr: CommandResult[F, S])(implicit hc: HandleContext): DBIOAction[Either[F, S], NoStream, Read with Write with Transactional] = {
    val txId = idGenerator.nextId()
    wrapInTxAndSchedule(cr
      .flatMap {
        case (result, partialEvents) =>
          handleEvents(partialEvents, hc, txId).map(handledEvents => (result, handledEvents))
      })
  }

  private[events] def runAsync[T](e: Event[T], hc: HandleContext): Future[Unit] = {
    database.db.run(handleAsync(e, hc))
  }

  private[events] def handleAsync[T](e: Event[T], hc: HandleContext): DBIOAction[Unit, NoStream, Read with Write with Transactional] = {
    val txId = idGenerator.nextId()
    val listenerResults = registry.lookupAsyncEventListeners(e).map { listener =>
      for {
        events <- listener(e)
        handledEvents <- handleEvents(events, hc, txId)
      } yield handledEvents
    }

    wrapInTxAndSchedule(DBIO.sequence(listenerResults).map(_.flatten).map(((), _)))
  }

  private def wrapInTxAndSchedule[T](action: DBIOAction[(T, List[Event[_]]), NoStream, Read with Write]): DBIOAction[T, NoStream, Read with Write with Transactional] = {
    import database.driver.api._
    action
      .transactionally
      .map {
        case (result, handledEvents) =>
          asyncEventScheduler.schedule(handledEvents.filter(registry.hasAsyncEventListeners))
          result
      }
  }

  private type DBHandledEvents = DBIOAction[List[Event[_]], NoStream, Read with Write]

  private def handleEvents(pes: Seq[PartialEvent[_, _]], originalCtx: HandleContext, txId: Long): DBHandledEvents = {
    val allOps = pes.foldLeft((originalCtx, List.empty[DBHandledEvents])) {
      case ((currentCtx, ops), pe) =>
        val peIds = pe.withIds

        val ctx = pe.data match {
          case t: HandleContextTransform[Any @unchecked] => t(peIds.asInstanceOf[PartialEventWithId[Any, _]], currentCtx)
          case _ => currentCtx
        }

        val e = peIds.toEvent(ctx.rawUserId, txId)

        val op = handleEvent(e, ctx, txId)

        (ctx, op :: ops)
    }._2.reverse

    DBIO.sequence(allOps).map(_.flatten)
  }

  private def handleEvent[T](e: Event[T], ctx: HandleContext, txId: Long): DBHandledEvents = {
    logger.info("Handling event: " + e)

    val doStore = eventStore.store(e.toStoredEvent).map(_ => List(e))

    val executeModelUpdate = DBIO.seq(registry.lookupModelUpdates(e).map(_(e)): _*).map(_ => Nil)

    val listenerResults = registry.lookupEventListeners(e).map { listener =>
      for {
        events <- listener(e)
        handledEvents <- handleEvents(events, ctx, txId)
      } yield handledEvents
    }

    DBIO.sequence(doStore :: executeModelUpdate :: listenerResults).map(_.flatten)
  }
}