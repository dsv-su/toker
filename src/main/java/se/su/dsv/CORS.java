package se.su.dsv;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@WebFilter(urlPatterns = "/token", displayName = "CORS")
public class CORS implements Filter {
    /**
     * The {@code Origin} header must <strong>end</strong> with one of these
     */
    private static final Set<String> ALLOWED_ORIGINS;
    private static final Set<String> ALLOWED_METHODS;
    private static final Duration MAX_AGE;

    static {
        ALLOWED_ORIGINS = Collections.singleton("dsv.su.se");
        ALLOWED_METHODS = Collections.singleton("GET");
        MAX_AGE = Duration.ofDays(1);
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            final HttpServletRequest httpRequest = (HttpServletRequest) request;
            final HttpServletResponse httpResponse = (HttpServletResponse) response;

            final String origin = httpRequest.getHeader("Origin");
            final String acrm = httpRequest.getHeader("Access-Control-Request-Method");
            final String method = httpRequest.getMethod();

            if (Objects.equals("OPTIONS", method) && nonNull(origin) && nonNull(acrm) && allowCORS(origin, acrm)) {
                addHeaders(origin, httpResponse);
            }
            else if (nonNull(origin) && allowCORS(origin, method)) {
                addHeaders(origin, httpResponse);
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allowCORS(final String origin, final String acrm) {
        final boolean originAllowed = ALLOWED_ORIGINS.stream()
                .anyMatch(origin::endsWith);
        final boolean methodAllowed = ALLOWED_METHODS.contains(acrm);
        return originAllowed && methodAllowed;
    }

    private void addHeaders(final String origin, final HttpServletResponse httpResponse) {
        httpResponse.addHeader("Vary", "Origin, Access-Control-Request-Method");
        httpResponse.addHeader("Access-Control-Allow-Origin", origin);
        httpResponse.addHeader("Access-Control-Allow-Methods", ALLOWED_METHODS.stream().collect(Collectors.joining(", ")));
        httpResponse.addHeader("Access-Control-Max-Age", Long.toString(MAX_AGE.getSeconds()));
    }

    @Override
    public void destroy() {

    }
}
