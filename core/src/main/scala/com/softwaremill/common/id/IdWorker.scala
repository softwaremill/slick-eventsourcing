/** Copyright 2010-2011 Twitter, Inc.*/
package com.softwaremill.common.id

import com.typesafe.scalalogging.StrictLogging

/**
 * An object that generates IDs.
 * This is broken into a separate class in case
 * we ever want to support multiple worker threads
 * per process
 *
 * Copied from: https://github.com/twitter/snowflake/tree/master/src/main/scala/com/twitter/service/snowflake
 * Modified to fit our logging, removed stats.
 *
 * Single threaded!
 */
private[id] class IdWorker(workerId: Long, datacenterId: Long, var sequence: Long = 0L) extends StrictLogging {
  val twepoch = 1288834974657L

  private val workerIdBits = 5L
  private val datacenterIdBits = 5L
  private val maxWorkerId = -1L ^ (-1L << workerIdBits)
  private val maxDatacenterId = -1L ^ (-1L << datacenterIdBits)
  private val sequenceBits = 12L

  private val workerIdShift = sequenceBits
  private val datacenterIdShift = sequenceBits + workerIdBits
  private val timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits
  private val sequenceMask = -1L ^ (-1L << sequenceBits)

  private var lastTimestamp = -1L

  // sanity check for workerId
  if (workerId > maxWorkerId || workerId < 0) {
    throw new IllegalArgumentException("worker Id can't be greater than %d or less than 0".format(maxWorkerId))
  }

  if (datacenterId > maxDatacenterId || datacenterId < 0) {
    throw new IllegalArgumentException("datacenter Id can't be greater than %d or less than 0".format(maxDatacenterId))
  }

  logger.info("Id worker starting. Timestamp left shift %d, datacenter id bits %d, worker id bits %d, sequence bits %d, workerid %d".format(
    timestampLeftShift, datacenterIdBits, workerIdBits, sequenceBits, workerId
  ))

  def get_worker_id(): Long = workerId
  def get_datacenter_id(): Long = datacenterId
  def get_timestamp() = System.currentTimeMillis

  def nextId(): Long = synchronized {
    var timestamp = timeGen()

    if (lastTimestamp == timestamp) {
      sequence = (sequence + 1) & sequenceMask
      if (sequence == 0) {
        timestamp = tilNextMillis(lastTimestamp)
      }
    }
    else {
      sequence = 0
    }

    if (timestamp < lastTimestamp) {
      logger.error("Clock is moving backwards. Rejecting requests until %d.".format(lastTimestamp))
      throw new RuntimeException("Invalid system clock: Clock moved backwards. Refusing to generate id for %d milliseconds".format(lastTimestamp - timestamp))
    }

    lastTimestamp = timestamp
    ((timestamp - twepoch) << timestampLeftShift) |
      (datacenterId << datacenterIdShift) |
      (workerId << workerIdShift) |
      sequence
  }

  protected def tilNextMillis(lastTimestamp: Long): Long = {
    var timestamp = timeGen()
    while (timestamp <= lastTimestamp) {
      timestamp = timeGen()
    }
    timestamp
  }

  protected def timeGen(): Long = System.currentTimeMillis()
}
