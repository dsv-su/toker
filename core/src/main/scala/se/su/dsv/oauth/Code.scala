package se.su.dsv.oauth

import java.util.UUID

import org.http4s._

final case class Code(redirectUri: Option[Uri], uuid: UUID, payload: Payload, proofKey: Option[ProofKey])

object Code {
  implicit object instances extends QueryParam[Code] with QueryParamEncoder[Code] {
    override def key: QueryParameterKey = QueryParameterKey("code")

    override def encode(code: Code): QueryParameterValue = QueryParameterValue(code.uuid.toString)
  }
}
