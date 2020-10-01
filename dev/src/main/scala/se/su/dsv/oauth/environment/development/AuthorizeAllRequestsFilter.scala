package se.su.dsv.oauth
package environment
package development

import javax.servlet.{Filter, FilterChain, FilterConfig, ServletRequest, ServletResponse}
import javax.servlet.annotation.WebFilter
import javax.servlet.http.{HttpServletRequest, HttpServletRequestWrapper}

@WebFilter
class AuthorizeAllRequestsFilter extends Filter {
  override def init(filterConfig: FilterConfig): Unit = ()

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    request.setAttribute("entitlement", developerEntitlement)
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
