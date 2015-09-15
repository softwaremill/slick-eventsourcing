package com.softwaremill.example.user

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import com.softwaremill.events.UserType
import com.softwaremill.example.common.Utils
import org.joda.time.DateTime
import com.softwaremill.macwire._

case class User(
  id: Long @@ User,
  login: String,
  loginLowerCased: String,
  email: String,
  password: String,
  salt: String,
  lastLogin: Option[DateTime]
)

object User {
  def encryptPassword(password: String, salt: String): String = {
    // 10k iterations takes about 10ms to encrypt a password on a 2013 MacBook
    val keySpec = new PBEKeySpec(password.toCharArray, salt.getBytes, 10000, 128)
    val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val bytes = secretKeyFactory.generateSecret(keySpec).getEncoded
    Utils.toHex(bytes)
  }

  def passwordsMatch(plainPassword: String, user: User) = {
    user.password.equals(encryptPassword(plainPassword, user.salt))
  }

  implicit val userTypeIsUser: UserType[User] = null
}
