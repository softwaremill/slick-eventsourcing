package com.softwaremill.example.user

case class UserJson(id: String, login: String, email: String)

object UserJson {
  def apply(user: User) = new UserJson(user.id.toString, user.login, user.email)

  def apply(list: List[User]): List[UserJson] = {
    for (user <- list) yield UserJson(user)
  }

  def apply(userOpt: Option[User]): Option[UserJson] = {
    userOpt match {
      case Some(user) => new Some(UserJson(user))
      case _ => None
    }
  }
}
