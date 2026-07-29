package dev.social.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies the access tokens.
 *
 * <p>Only auth-service calls {@link #issue}; the other three services only ever
 * call {@link #parse} through {@link JwtAuthenticationFilter}.
 *
 * <p>Token layout:
 * <pre>
 *   sub  the user id (UUID)   - the identity everything else keys off
 *   iss  configured issuer    - rejects tokens signed by another system
 *   iat  issued at
 *   exp  expiry
 *   jti  unique token id      - the hook a future revocation list would use
 *   username  convenience copy
 *   roles     list of authority names
 * </pre>
 *
 * <p>No email, no name, no birth date. A JWT is signed but <em>not</em>
 * encrypted: anyone holding it can read every claim, so personal data stays in
 * profile-service behind an authorised call.
 */
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    static final String CLAIM_USERNAME = "username";
    static final String CLAIM_ROLES = "roles";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;

        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < JwtProperties.MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least %d bytes for HS256 (got %d). "
                            .formatted(JwtProperties.MINIMUM_SECRET_BYTES, keyBytes.length)
                            + "Generate one with: openssl rand -base64 48");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // -------------------------------------------------------------------------
    //  Issuing
    // -------------------------------------------------------------------------

    public IssuedToken issue(UUID userId, String username, List<String> roles) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());

        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, issuedAt, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    // -------------------------------------------------------------------------
    //  Verifying
    // -------------------------------------------------------------------------

    /**
     * @throws ExpiredJwtException when the token has simply run out - the caller
     *         distinguishes this from a bad token so the frontend can react
     *         differently
     * @throws JwtException        for a bad signature, wrong issuer, or anything
     *         malformed
     */
    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .clockSkewSeconds(properties.clockSkew().toSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get(CLAIM_USERNAME, String.class);

        List<String> roles = claims.get(CLAIM_ROLES, List.class) instanceof List<?> rawRoles
                ? rawRoles.stream().map(String::valueOf).toList()
                : List.of();

        return new AuthenticatedUser(userId, username, roles);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("Token rejected: {}", exception.getMessage());
            return false;
        }
    }

    public record IssuedToken(String token, Instant issuedAt, Instant expiresAt, long expiresInSeconds) {
    }
}
