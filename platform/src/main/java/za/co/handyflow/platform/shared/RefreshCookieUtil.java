package za.co.handyflow.platform.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Builds the httpOnly refresh-token cookie. Kept as a small, dedicated
 * class rather than inlined in the controller, since these attributes are
 * exactly the ones that are easy to get subtly wrong and hard to notice
 * when wrong (a cookie that silently never gets sent looks, from the
 * outside, identical to "the user just isn't logged in" — no error, no
 * stack trace, just a refresh that mysteriously never works).
 * <p>
 * WHY configurable Secure/SameSite rather than hardcoded?
 * <p>
 * The frontend (localhost:5173) and backend (localhost:8080) are
 * different origins even in local dev — different ports count as
 * cross-origin for cookie purposes. A cross-origin cookie requires
 * SameSite=None, and modern browsers (Chrome specifically) require
 * SameSite=None to also carry Secure=true — which normally means
 * HTTPS-only. Production should run Secure=true, SameSite=None over real
 * HTTPS with no issue. Local dev over plain HTTP genuinely cannot satisfy
 * that combination — Chrome will silently refuse to send the cookie at
 * all, no error surfaced anywhere.
 * <p>
 * Two ways to actually run this locally, neither of which is a code
 * change here:
 * 1. Set app.security.cookie.secure=false in a local/dev profile and
 *    accept that this technically violates the Secure+SameSite=None
 *    browser rule — in practice this still works in most browsers for
 *    plain localhost testing, but it's not a production-representative
 *    test of the real cookie behaviour.
 * 2. (Recommended, more accurately reproduces production behaviour) Add a
 *    Vite dev-server proxy so the browser only ever talks to
 *    localhost:5173, with Vite forwarding /api/* calls to :8080
 *    server-side — this makes frontend and backend same-origin from the
 *    browser's perspective, sidestepping the whole SameSite question
 *    locally. Not implemented here — needs a vite.config.ts change this
 *    class has no visibility into.
 */
@Component
public class RefreshCookieUtil {

    public static final String COOKIE_NAME = "refresh_token";

    @Value("${app.security.cookie.secure:true}")
    private boolean secure;

    @Value("${app.security.cookie.same-site:None}")
    private String sameSite;

    /**
     * Path deliberately narrowed to /api/v1/auth — this cookie has no
     * business being attached to every single API call the frontend
     * makes; it's only ever needed by the handful of auth endpoints that
     * actually read it.
     */
    public ResponseCookie build(String rawToken, Instant expiresAt) {
        long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** Clears the cookie — logout, or a refresh attempt that failed hard enough to end the session. */
    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}