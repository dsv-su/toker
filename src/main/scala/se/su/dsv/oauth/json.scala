package se.su.dsv.oauth

import java.time.Duration

import argonaut.CodecJson

object json {
  implicit val durationCodecJson: CodecJson[Duration] =
    CodecJson.derived[Long].xmap(Duration.ofSeconds)(_.getSeconds)
}
