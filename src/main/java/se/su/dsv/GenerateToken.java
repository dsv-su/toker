package se.su.dsv;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static se.su.dsv.json.Json.jNumber;
import static se.su.dsv.json.Json.jObject;
import static se.su.dsv.json.Tuple.kv;

@WebServlet("/token")
public class GenerateToken extends HttpServlet
{
    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException
    {
        final String remoteUser = req.getRemoteUser();
        if (remoteUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        else {
            final Instant generated = Instant.now();
            final Profile profile = new Profile(remoteUser);
            final Token token = Token.generate(generated, profile);
            resp.getWriter().println(encode(token));
        }
    }

    private String encode(final Token token) {
        final String metadata = encodeMetadata(token.getMetadata());
        return token.getUuid().toString() + "." + metadata;
    }

    private String encodeMetadata(final Token.Metadata metadata) {
        final String json = jObject(
                kv("exp", jNumber(metadata.getExpires().getEpochSecond()))
        ).toString();
        return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
