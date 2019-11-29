package se.su.dsv

import cats.effect.IO
import io.chrisdavenport.vault.Key

package object oauth {
  val RemoteUser: Key[String] = Key.newKey[IO, String].unsafeRunSync()
  val DisplayName: Key[String] = Key.newKey[IO, String].unsafeRunSync()
  val EntitlementsKey: Key[Entitlements] = Key.newKey[IO, Entitlements].unsafeRunSync()
  val Mail: Key[String] = Key.newKey[IO, String].unsafeRunSync()

  val entitlementPrefix = "urn:mace:swami.se:gmai:dsv-user"
}
