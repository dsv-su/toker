package se.su.dsv.oauth

import java.time.Duration

import io.circe._

object json {
  import io.circe.Encoder._
  import io.circe.Decoder._
  implicit val durationCodecJson: Encoder[Duration] =
    Encoder[Long].contramap[Duration](_.getSeconds)
  implicit val durationDecoder: Decoder[Duration] =
    Decoder[Long].map(Duration.ofSeconds)
}
