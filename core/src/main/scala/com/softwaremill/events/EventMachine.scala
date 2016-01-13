package com.softwaremill.events

import java.time.{Clock, OffsetDateTime}

import com.softwaremill.id.IdGenerator
import com.typesafe.scalalogging.StrictLogging
import slick.backend.DatabasePublisher
import slick.dbio.Effect.{Read, Transactional, Write}
import slick.dbio.{DBIO, DBIOAction, NoStream}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class EventMachine(
    database: EventsDatabase,
    registry: Registry,
    eventStore: EventStore,
    asyncEventScheduler: AsyncEventScheduler,
    idGenerator: IdGenerator,
    clock: Clock
)(implicit ec: ExecutionContext) extends StrictLogging {

  /**
   * Runs the database actions described by the `cr: CommandResult` to compute the return value: either success (`: S`)
   * or failure (`: F`)) and a list of events. For each event, first runs the model update functions, then the event
   * listeners. In case new events are created, handles them recursively.
   *
   * The actions to compute the command result, the model updates and the event listeners are **executed in a single
   * DB transaction**.
   *
   * Finally, schedules asynchronous event listeners for the events created to run in separate transactions (if any).
   */
  def run[F, S](cr: CommandResult[F, S])(implicit hc: HandleContext): Future[Either[F, S]] = {
    database.db.run(handle(cr))
  }

  /**
   * Same as [[run]], but returns a `DBAction` describing the actions that should be done to handle the given
   * command result and created events transactionally.
   */
  def handle[F, S](cr: CommandResult[F, S])(implicit hc: HandleContext): DBIOAction[Either[F, S], NoStream, Read with Write with Transactional] = {
    val txId = idGenerator.nextId()
    wrapInTxAndSchedule(cr
      .flatMap {
        case (result, partialEvents) =>
          handleEvents(partialEvents, hc, txId).map(handledEvents => (result, handledEvents))
      })
  }

  /**
   * Recovers db state from event log.
   *
   * @param timeLimit Optional point in time till which events should be recovered.
   * @return
   */
  def failOverFromStoredEvents(timeLimit: OffsetDateTime = OffsetDateTime.now()): Future[Unit] = {
    type Action = DBIOAction[Unit, NoStream, Read with Write]

    def lookupModelUpdates(eOpt: Option[Event[Any]]): List[Action] = eOpt match {
      case Some(e) => registry.lookupModelUpdates(e).map(_(e))
      case None => List()
    }

    def performActionInTx(action: Action) = {
      import database.driver.api._
      database.db.run(action.transactionally)
    }

    def toEventIfHasModelUpdate(e: StoredEvent): Option[Event[Any]] = {
      registry.eventClassIfHasModelUpdate(e.eventType)
        .flatMap(cls => e.toEvent(cls, registry.formatsForEvent(e.eventType)) match {
          case Success(event) => Some(event)
          case Failure(ex) => logger.warn("Couldn't deserialize JSON", ex); None
        })
    }

    val storedEvents: DatabasePublisher[StoredEvent] = database.db.stream(eventStore.getAll(timeLimit))

    val eventActions = storedEvents
      .mapResult(toEventIfHasModelUpdate)
      .mapResult(lookupModelUpdates)

    val doCountEvents = () => database.db.run(eventStore.getLength(registry.eventTypesWithModelUpdates))
      .map(storedEventsCount => logger.info(s"Number of events to recover: $storedEventsCount"))

    val doRecover = () => eventActions.foreach(_.foreach(e => performActionInTx(e).andThen {
      case dbResponse => logger.info(s"Recovery db result: $dbResponse")
    }))

    for {
      _ <- doCountEvents()
      _ <- doRecover()
    } yield ()
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
        val peIds = pe.withIds(idGenerator, clock)

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

    val doStore = eventStore.store(e.toStoredEvent(registry.formatsForEvent(e.eventType))).map(_ => List(e))

    val executeModelUpdate = DBIO.seq(registry.lookupModelUpdates(e).map(_ (e)): _*).map(_ => Nil)

    val listenerResults = registry.lookupEventListeners(e).map { listener =>
      for {
        events <- listener(e)
        handledEvents <- handleEvents(events, ctx, txId)
      } yield handledEvents
    }

    DBIO.sequence(doStore :: executeModelUpdate :: listenerResults).map(_.flatten)
  }
}
