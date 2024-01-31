package se.su.dsv.oauth.embedded

import se.su.dsv.oauth.entitlementPrefix
import se.su.dsv.oauth.environment.developerEntitlement

import javax.servlet.{Filter, FilterChain, FilterConfig, ServletRequest, ServletResponse}
import javax.servlet.annotation.WebFilter
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}

class AuthorizeAllRequestsFilter(extraEntitlement: Option[String]) extends Filter {
  override def init(filterConfig: FilterConfig): Unit = ()

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    request.setAttribute("entitlement", developerEntitlement + extraEntitlement.map(s";$entitlementPrefix:" + _).getOrElse(""))
    request match {
      case httpServletRequest: HttpServletRequest =>
        val wrapper = new HttpServletRequestWrapper(httpServletRequest) {
          override def getRemoteUser: String = "dev@localhost"
        }
        chain.doFilter(wrapper, response)
      case _ =>
        chain.doFilter(request, response)
    }
  }

  override def destroy(): Unit = ()
}
