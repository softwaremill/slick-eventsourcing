package com.softwaremill.example

import slick.dbio.Effect.{Read, Write}
import slick.dbio.{DBIOAction, NoStream}

package object database {
  type DBRead[R] = DBIOAction[R, NoStream, Read]
  type DBWrite = DBIOAction[Unit, NoStream, Write]
}
