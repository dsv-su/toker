# toker
Toker is a bridge between Shibboleth (SAML) and OAuth 2.0.

The administration GUI is available under `/admin`.

* Authorization end point `/authorize`
* Token end point `/exchange`
* Introspection end point `/introspect`

## OAuth 2.0 details

### Authorization grant
The only supported method is [authorization code grant](https://www.rfc-editor.org/rfc/rfc6749#section-4.1).
[PKCE](https://www.rfc-editor.org/rfc/rfc7636) is mandatory for public clients and optional for confidential.

### Scopes
Scopes are, for now, unused by Toker itself but may be used by specific resource servers.

## Developing
### Locally
Run `sbt` and then `~dev/Jetty/debug`.
This will run the application on port 8080 with remote JVM debugging available on port 8888.
All sources will be live reloaded.
`dev/Jetty/stop` to stop the application.

#### Requirements
* Java
* [SBT](https://www.scala-sbt.org/index.html)
* MariaDB

#### MariaDB configuration
Needs to have a database called `oauth` accessible by the user `oauth` with the password `oauth`.
See `jetty.xml` for exact details.

### Docker
There is a compose file available that will run everything for you, including MariaDB.
Run `docker compose -f compose-local.yml up`.
Application available on port 8080 and remote JVM debugging on 8888.
All sources will be live reloaded.

#### Requirements
* Docker environment

## Test environment
To help use this when developing other services the test environment allows you,
with the right entitlements configured in SUKAT, to issue arbitrary tickets.
The entitlement required is (fully qualified) `urn:mace:swami.se:gmai:dsv-user:toker-test`.
