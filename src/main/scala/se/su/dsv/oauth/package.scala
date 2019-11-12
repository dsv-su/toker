package se.su.dsv

import org.http4s.AttributeKey

package object oauth {
  val RemoteUser: AttributeKey[String] = AttributeKey[String]
}
