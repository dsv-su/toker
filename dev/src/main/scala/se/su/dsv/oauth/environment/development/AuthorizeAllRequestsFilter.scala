package se.su.dsv.oauth
package environment
package development

import javax.servlet.{Filter, FilterChain, FilterConfig, ServletRequest, ServletResponse}
import javax.servlet.annotation.WebFilter

@WebFilter
class AuthorizeAllRequestsFilter extends Filter {
  override def init(filterConfig: FilterConfig): Unit = ()

  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    request.setAttribute("entitlement", developerEntitlement)
    chain.doFilter(request, response)
  }

  override def destroy(): Unit = ()
}
