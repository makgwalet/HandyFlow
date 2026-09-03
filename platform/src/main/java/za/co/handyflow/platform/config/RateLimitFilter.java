package za.co.handyflow.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.handyflow.platform.shared.RateLimiter;

import java.io.IOException;

/**
 * Applies RateLimiter to the specific public, unauthenticated endpoints
 * that need it. See HandyFlow BOS Discovery doc, Section 19.3/20.1 for
 * the fuller list of confirmed unauthenticated portals (Clinic, Projects,
 * Desk Support, Recruiter, Creative) that should get the same treatment —
 * adding another module's public path here is a one-line addition to
 * LIMITS below, not a new mechanism.
 * <p>
 * UPDATED (Section 42/43): added the accountant client portal's own
 * auth endpoints (/api/v1/accountant/portal/auth/register and
 * .../login). These were found to be just as unauthenticated and
 * brute-forceable as the main app's /api/v1/auth/** endpoints — same
 * permitAll() exposure, same risk, just a different module's login form.
 * Confirmed exact paths directly against AccountantPortalAuthController
 * rather than guessed.
 * <p>
 * WHY THIS LIVES IN `config`, NOT `shared`: this filter needs to know
 * about specific request paths belonging to other modules — that's
 * exactly the kind of cross-cutting composition-root concern
 * SecurityConfig already lives here for (see that class's own Javadoc
 * from the SecurityConfig relocation earlier in this engagement).
 * RateLimiter itself stays in `shared` because it's genuinely generic;
 * only the "which paths get which limits" wiring belongs here.
 * <p>
 * CLIENT IP CAVEAT — IMPORTANT, READ BEFORE PUTTING THIS BEHIND A PROXY:
 * this reads request.getRemoteAddr() directly, which is correct and safe
 * ONLY as long as there is no reverse proxy/load balancer/WAF in front of
 * the app (confirmed true for the current development environment as of
 * this fix). The moment one is added, getRemoteAddr() will return the
 * proxy's own IP for every request — collapsing every real client onto
 * one shared rate-limit bucket (locking everyone out together) rather
 * than limiting per real client. At that point this MUST switch to
 * reading X-Forwarded-For (or the proxy's equivalent header), and that
 * header must only be trusted when the request genuinely came through
 * the known proxy — trusting X-Forwarded-For from the public internet
 * directly lets any caller spoof their rate-limit identity. Flagging
 * this explicitly now so it isn't forgotten when infra changes, per
 * Q14's answer that no gateway exists yet.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    // scope:path-prefix -> {maxRequests, windowMs}
    private record Limit(String scope, String pathPrefix, int maxRequests, long windowMs) {}

    private static final Limit[] LIMITS = {
            // Main app registration: generous enough for a real user retrying
            // a typo, tight enough to stop scripted mass signup against the
            // free pilot.
            new Limit("auth:register", "/api/v1/auth/register", 5, 60 * 60 * 1_000L),   // 5/hour

            // Main app login: generous enough for a real user who mistypes a
            // password a few times, tight enough to meaningfully slow
            // brute-forcing.
            new Limit("auth:login", "/api/v1/auth/login", 10, 10 * 60 * 1_000L),        // 10/10min

            // Accountant client portal — invite-based registration. Tighter
            // than main-app register() since this is invite-only (a
            // legitimate user only ever calls this once, when accepting
            // their invite) — no reason for repeated legitimate attempts the
            // way a typo'd signup form might see.
            new Limit("portal:register", "/api/v1/accountant/portal/auth/register", 5, 60 * 60 * 1_000L),

            // Accountant client portal — login. Same limit as main-app login;
            // same brute-force risk, just a different set of credentials.
            new Limit("portal:login", "/api/v1/accountant/portal/auth/login", 10, 10 * 60 * 1_000L),
            new Limit("payrollportal:register", "/api/v1/payroll-bureau/portal/auth/register", 5, 60 * 60 * 1_000L),
            new Limit("payrollportal:login", "/api/v1/payroll-bureau/portal/auth/login", 10, 10 * 60 * 1_000L),

            // NEW (identity module modernization): main-app invitation
            // acceptance — just made reachable via SecurityConfig's
            // permitAll() fix above. Same risk shape as
            // "portal:register" (invite-only, a legitimate user only
            // ever calls this once) rather than open registration, so
            // the same 5/hour ceiling applies.
            new Limit("identity:invitation-accept", "/api/v1/identity/invitations/accept", 5, 60 * 60 * 1_000L),
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        for (Limit limit : LIMITS) {
            if (path.equals(limit.pathPrefix())) {
                String clientIp = request.getRemoteAddr(); // see class Javadoc CLIENT IP CAVEAT
                String key = limit.scope() + ":" + clientIp;

                if (!rateLimiter.tryConsume(key, limit.maxRequests(), limit.windowMs())) {
                    response.setStatus(429); // HttpServletResponse has no TOO_MANY_REQUESTS constant pre-Servlet 6
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"success\":false,\"message\":\"Too many requests — please try again shortly.\"}");
                    return; // don't call filterChain.doFilter — request stops here
                }
                break;
            }
        }

        filterChain.doFilter(request, response);
    }
}