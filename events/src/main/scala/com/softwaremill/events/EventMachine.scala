package com.softwaremill.events

import com.softwaremill.common.Clock
import com.softwaremill.common.id.IdGenerator
import com.softwaremill.database.{DBReadWrite, SqlDatabase}
import com.typesafe.scalalogging.StrictLogging
import slick.dbio.Effect.{Read, Transactional, Write}
import slick.dbio.{DBIO, DBIOAction, NoStream}

import scala.concurrent.ExecutionContext

class EventMachine(
    database: SqlDatabase,
    registry: Registry,
    eventStore: EventStore
)(implicit
  ec: ExecutionContext,
    idGenerator: IdGenerator,
    clock: Clock) extends StrictLogging {

  def handle[F, S](cr: CommandResult[F, S])(implicit originalCtx: HandleContext): DBIOAction[Either[F, S], NoStream, Read with Write with Transactional] = {
    import database.driver.api._
    val ctx = originalCtx.withNewTxIdIfUnset._1
    cr.flatMap {
      case (result, partialEvents) =>
        handleEvents(partialEvents: _*)(ctx).map(_ => result)
    }.transactionally
  }

  def handleEvents(pes: PartialEvent[_, _]*)(implicit originalCtx: HandleContext): DBReadWrite = {
    val allOps = pes.foldLeft((originalCtx, List.empty[DBReadWrite])) {
      case ((currentCtx, ops), pe) =>
        val peIds = pe.withIds

        val (ctxWithTxId, txId) = currentCtx.withNewTxIdIfUnset
        val ctx = pe.data match {
          case t: HandleContextTransform[Any @unchecked] => t(peIds.asInstanceOf[PartialEventWithId[Any, _]], ctxWithTxId)
          case _ => ctxWithTxId
        }

        val e = peIds.toEvent(ctx.rawUserId, txId)

        val op = handleEvent(e, ctx)

        (ctx, op :: ops)
    }._2.reverse

    DBIO.seq(allOps: _*)
  }

  private def handleEvent[T](e: Event[T], ctx: HandleContext): DBReadWrite = {
    logger.info("Handling event: " + e)

    val doStore = eventStore.store(e.toStoredEvent)

    val executeModelUpdate = DBIO.seq(registry.lookupModelUpdate(e).map(_(e)): _*)

    val listenerResults = registry.lookupEventListener(e).map { listener =>
      for {
        events <- listener(e)
        _ <- handleEvents(events: _*)(ctx)
      } yield ()
    }

    DBIO.seq(doStore, executeModelUpdate, DBIO.seq(listenerResults: _*))
  }
}
