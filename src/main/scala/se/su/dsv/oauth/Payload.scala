package se.su.dsv.oauth

import argonaut._, Argonaut._

final case class Payload(principal: String)
object Payload {
  implicit val payloadEncodeJson: CodecJson[Payload] =
    casecodec1(Payload.apply, Payload.unapply)("principal")
}
