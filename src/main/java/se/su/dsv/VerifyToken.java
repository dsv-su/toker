package se.su.dsv;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

import static se.su.dsv.json.Json.jObject;
import static se.su.dsv.json.Json.jString;
import static se.su.dsv.json.Tuple.kv;

@WebServlet("/verify")
public class VerifyToken extends HttpServlet
{
    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException
    {
        final String body = readBody(req);
        try {
            final UUID uuid = UUID.fromString(body);
            final Token token = Token.get(Instant.now(), uuid);
            if (token != null) {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setHeader("Content-Type", "application/json");
                resp.getWriter().println(toJson(token.getPayload()));
            }
            else {
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        }
        catch (IllegalArgumentException ignored) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    private String toJson(final Profile payload) {
        return jObject(
                kv("principal", jString(payload.getPrincipal()))
        ).toString();
    }

    private String readBody(final HttpServletRequest req) throws IOException {
        try (Reader in = req.getReader()) {
            final StringWriter out = new StringWriter();
            char[] buffer = new char[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toString();
        }
    }
}
