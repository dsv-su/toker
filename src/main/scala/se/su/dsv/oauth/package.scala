package se.su.dsv

import cats.effect.IO
import io.chrisdavenport.vault.Key

package object oauth {
  val RemoteUser: Key[String] = Key.newKey[IO, String].unsafeRunSync()
}
