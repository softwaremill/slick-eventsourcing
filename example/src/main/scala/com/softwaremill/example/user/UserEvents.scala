package com.softwaremill.example.user

import com.softwaremill.events._

case class UserRegistered(
    login: String,
    email: String,
    encryptedPassword: String,
    salt: String
) extends HandleContextTransform[User] {
  override def apply(e: PartialEventWithId[User, _], hc: HandleContext) = hc.withUserId(e.aggregateId)
}

object UserRegistered {
  implicit val afe = AggregateForEvent[UserRegistered, User]
}

case class UserLoggedIn() extends HandleContextTransform[User] {
  override def apply(e: PartialEventWithId[User, _], hc: HandleContext) = hc.withUserId(e.aggregateId)
}

object UserLoggedIn extends AggregateForEvent[UserLoggedIn, User]
