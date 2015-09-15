package com.softwaremill.example.common

import scala.util.Random

object Utils {
  def randomString(length: Int) = Random.alphanumeric take length mkString ""

  // see http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
  private val hexArray = "0123456789ABCDEF".toCharArray
  def toHex(bytes: Array[Byte]): String = {
    val hexChars = new Array[Char](bytes.length * 2)
    for (j <- 0 until bytes.length) {
      val v = bytes(j) & 0xFF
      hexChars(j * 2) = hexArray(v >>> 4)
      hexChars(j * 2 + 1) = hexArray(v & 0x0F)
    }
    new String(hexChars)
  }
}
