package se.su.dsv;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Token {
    private static final Duration VALID_FOR = Duration.ofMinutes(30);
    private static final ConcurrentHashMap<UUID, Token> TOKENS = new ConcurrentHashMap<>();

    public static Token generate(Instant now, Profile profile) {
        removeExpired(now);
        final UUID uuid = UUID.randomUUID();
        final Instant expires = now.plus(VALID_FOR);
        final Token token = new Token(uuid, new Metadata(expires), profile);
        TOKENS.put(uuid, token);
        return token;
    }

    private static void removeExpired(final Instant now) {
        TOKENS.values().removeIf(token -> token.isExpired(now));
    }

    public static Token get(Instant now, final UUID uuid) {
        final Token token = TOKENS.get(uuid);
        if (token == null || token.isExpired(now)) {
            return null;
        }
        else {
            return token;
        }
    }

    private final UUID uuid;
    private final Metadata metadata;
    private final Profile payload;

    private Token(final UUID uuid, final Metadata metadata, final Profile payload) {
        this.uuid = uuid;
        this.metadata = metadata;
        this.payload = payload;
    }

    private boolean isExpired(Instant now) {
        return now.isAfter(metadata.getExpires());
    }

    public UUID getUuid() {
        return uuid;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public Profile getPayload() {
        return payload;
    }

    public static final class Metadata {
        private final Instant expires;

        private Metadata(final Instant expires) {
            this.expires = expires;
        }

        public Instant getExpires() {
            return expires;
        }
    }
}
