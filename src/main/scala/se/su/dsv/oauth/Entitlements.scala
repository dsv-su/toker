package se.su.dsv.oauth

final case class Entitlements(values: List[String]) {
  def hasEntitlement(entitlement: String): Boolean = values.contains(entitlement)
}
