package se.su.dsv.oauth

import cats.data.EitherT
import cats.effect.Concurrent
import io.circe.*

import scala.collection.immutable.Set
import org.http4s.{EntityDecoder, Request, Uri, UrlForm}

sealed trait ResponseType
object ResponseType {
  case object Code extends ResponseType
  case object Token extends ResponseType
}

final case class AuthorizationRequest private (
  responseType: ResponseType,
  clientId: String,
  redirectUri: Option[Uri],
  scopes: Set[String],
  state: Option[String]
)
object AuthorizationRequest {
  def fromRaw[F[_]](request: Request[F]): Option[AuthorizationRequest] =
    helper(request.params.get)

  def fromForm(form: UrlForm): Option[AuthorizationRequest] =
    helper(form.getFirst)

  private def helper(getParam: String => Option[String]) =
    for {
      responseType <- getParam("response_type").flatMap {
        case "code" => Some(ResponseType.Code)
        case "token" => Some(ResponseType.Token)
        case _ => None
      }
      clientId <- getParam("client_id")
      redirectUri = getParam("redirect_uri").flatMap(Uri.fromString(_).toOption)
      scope = getParam("scope").map(_.split(' ').toSet).getOrElse(Set.empty)
      state = getParam("state")
    } yield {
      AuthorizationRequest(responseType, clientId, redirectUri, scope, state)
    }
}

final case class AccessTokenRequest private (
  code: String,
  redirectUri: Option[Uri]
)
object AccessTokenRequest {
  sealed trait ErrorResponse
  object ErrorResponse {
    case object InvalidRequest extends ErrorResponse
    case object InvalidClient extends ErrorResponse
    case object InvalidGrant extends ErrorResponse

    implicit val encodeJson: Encoder[ErrorResponse] =
      Encoder { error =>
        val errorString = error match {
          case InvalidRequest => "invalid_request"
          case InvalidClient => "invalid_client"
          case InvalidGrant => "invalid_grant"
        }
        Json.obj(("error", Json.fromString(errorString)))
      }
  }

  val invalidRequest: ErrorResponse = ErrorResponse.InvalidRequest
  val invalidClient: ErrorResponse = ErrorResponse.InvalidClient
  val invalidGrant: ErrorResponse = ErrorResponse.InvalidGrant

  def  fromRequest[F[_]: Concurrent](request: Request[F]): EitherT[F, ErrorResponse, AccessTokenRequest] = {
    def getRequest(form: UrlForm): Option[AccessTokenRequest] = {
      for {
        grantType <- form.getFirst("grant_type")
        if grantType == "authorization_code"
        code <- form.getFirst("code")
        redirectUri = form.getFirst("redirect_uri").flatMap(Uri.fromString(_).toOption)
      } yield AccessTokenRequest(code, redirectUri)
    }
    for {
      form <- EntityDecoder[F, UrlForm]
        .decode(request, strict = true)
        .leftMap(_ => invalidRequest)
      request <- EitherT.fromEither(getRequest(form).toRight(invalidRequest))
    } yield request
  }
}
