package com.softwaremill

import slick.dbio.Effect.{Write, Read}
import slick.dbio.{NoStream, DBIOAction}

package object database {
  type DBRead[R] = DBIOAction[R, NoStream, Read]
  type DBWrite = DBIOAction[Unit, NoStream, Write]
  type DBReadWrite = DBIOAction[Unit, NoStream, Read with Write]
}
