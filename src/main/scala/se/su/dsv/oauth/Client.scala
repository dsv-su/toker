package se.su.dsv.oauth

import org.http4s.Uri

import scala.collection.immutable.Set

final case class Client(
  name: String,
  secret: String,
  allowedScopes: Set[String],
  redirectUri: Uri
)
