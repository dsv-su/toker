package se.su.dsv

import cats.effect.SyncIO
import org.typelevel.vault.Key

package object oauth {
  val RemoteUser: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()
  val DisplayName: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()
  val EntitlementsKey: Key[Entitlements] = Key.newKey[SyncIO, Entitlements].unsafeRunSync()
  val Mail: Key[String] = Key.newKey[SyncIO, String].unsafeRunSync()

  val entitlementPrefix = "urn:mace:swami.se:gmai"
}
