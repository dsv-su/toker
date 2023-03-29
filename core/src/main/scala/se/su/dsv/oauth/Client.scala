package se.su.dsv.oauth

import org.http4s.Uri
import se.su.dsv.oauth.Client.Confidential

import scala.collection.immutable.Set

sealed trait Client {
  def isConfidential: Boolean = this match {
    case _: Client.Public => false
    case _: Client.Confidential => true
  }

  def allowedScopes: Set[String]

  def redirectUri: Uri
}

object Client {
  final case class Confidential(name: String, secret: String, allowedScopes: Set[String], redirectUri: Uri) extends Client
  final case class Public(name: String, allowedScopes: Set[String], redirectUri: Uri) extends Client
}
