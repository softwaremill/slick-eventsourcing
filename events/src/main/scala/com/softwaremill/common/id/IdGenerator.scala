package com.softwaremill.common.id

class IdGenerator(workerId: Long = 1, datacenterId: Long = 1) {
  private val idWorker = new IdWorker(workerId, datacenterId)

  def nextId(): Long = {
    synchronized {
      idWorker.nextId()
    }
  }
}
