package com.softwaremill.events

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{BlockingQueue, TimeUnit}

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

trait AsyncEventScheduler {
  def schedule(events: List[Event[_]]): Future[Unit]
}

class BlockingQueueAsyncEventScheduler(bq: BlockingQueue[Event[_]]) extends AsyncEventScheduler {
  def schedule(events: List[Event[_]]): Future[Unit] = {
    Future.successful(events.foreach(bq.add))
  }
}

class BlockingQueueAsyncEventRunner(bq: BlockingQueue[Event[_]], em: EventMachine)(implicit ec: ExecutionContext) extends StrictLogging {
  private val running = new AtomicBoolean(true)
  private val t = new Thread(new Runnable {
    override def run() = {
      while (running.get() || bq.size() > 0) {
        val eventOpt = Option(bq.poll(1, TimeUnit.SECONDS))
        eventOpt.foreach { event =>
          em.runAsync(event, new HandleContext(event.rawUserId)).onFailure {
            case e: Exception => logger.error("Exception when handling an asynchronous event", e)
          }
        }
      }
    }
  })

  def start(): Unit = {
    t.start()
  }

  def stop(): Unit = {
    running.set(false)
  }
}
