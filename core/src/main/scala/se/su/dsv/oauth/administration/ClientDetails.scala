package se.su.dsv.oauth.administration

final case class ClientDetails(id: String, name: String, secret: Option[String], scopes: Set[String], redirectUri: String)
