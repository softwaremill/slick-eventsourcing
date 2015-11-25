package com.softwaremill.example.api

import com.softwaremill.tagging._
import com.softwaremill.example.user.User
import com.softwaremill.session.{MultiValueSessionSerializer, SessionSerializer}

import scala.util.Try

case class Session(userId: Long @@ User)

object Session {
  implicit val serializer: SessionSerializer[Session, String] = new MultiValueSessionSerializer[Session](
    (t: Session) => Map("id" -> t.userId.toString),
    m => Try(Session(m("id").toLong.taggedWith[User]))
  )
}
