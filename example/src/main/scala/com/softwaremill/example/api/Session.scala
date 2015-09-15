package com.softwaremill.example.api

import com.softwaremill.macwire._
import com.softwaremill.example.user.User
import com.softwaremill.session.{SessionSerializer, ToMapSessionSerializer}

case class Session(userId: Long @@ User)

object Session {
  implicit val serializer: SessionSerializer[Session] = new ToMapSessionSerializer[Session] {
    override def serializeToMap(t: Session) = Map("id" -> t.userId.toString)
    override def deserializeFromMap(m: Map[String, String]) = Session(m("id").toLong.taggedWith[User])
  }
}
