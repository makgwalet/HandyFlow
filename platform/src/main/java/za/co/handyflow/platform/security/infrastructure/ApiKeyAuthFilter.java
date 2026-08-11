// security/infrastructure/ApiKeyAuthFilter.java

package za.co.handyflow.platform.security.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.handyflow.platform.security.application.internal.PublicApiService;
import za.co.handyflow.platform.security.domain.model.ApiKey;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ApiKeyAuthFilter — closes the "API keys exist in the DB but won't
 * authenticate" gap flagged in PublicApiController's own deployment note
 * and the platform audit ("PublicApiController's own doc comment states
 * keys exist in the DB but won't authenticate anything until
 * ApiKeyAuthFilter is added to the security chain").
 *
 * Key validation itself (hash + lookup + isValid() + recordUse()) was
 * ALREADY built in PublicApiService.validateKey() — this filter is only
 * the missing wiring: extract the key from the Authorization header, call
 * that existing method, and populate TenantContext/SecurityContext the
 * same way every other auth filter in this codebase does.
 *
 * WHY dispatch on the Authorization header scheme ("ApiKey ") rather than
 * a URL path prefix, unlike GuardJwtFilter's "/api/v1/guard/**" scoping?
 * An API key can call many different security endpoints (reports, sites,
 * incidents, etc. — whatever its scopePrefixesJson allows), not one fixed
 * path family. The header value itself is the only reliable signal that a
 * request is trying to authenticate as an API key rather than a normal
 * tenant user.
 *
 * WHY no permitAll() entry needed in SecurityConfig?
 * Unlike guard login (genuinely anonymous until authenticated) or the
 * public portal (token-in-URL is deliberately unauthenticated by design —
 * see PortalJwtFilter/SecurityClientPortalController), an API-key request
 * always carries a real credential. This filter authenticates it and sets
 * a valid Authentication, the same way GuardJwtFilter does for
 * /api/v1/guard/** — .anyRequest().authenticated() is satisfied downstream
 * without any new permitAll() path.
 *
 * WHY finally-based cleanup, not GuardJwtFilter's clear-after-doFilter?
 * GuardJwtFilter clears SecurityContextHolder AFTER chain.doFilter()
 * completes, with no try/finally -- if anything downstream throws, that
 * cleanup line never runs and stale context leaks into the thread-pool
 * thread's NEXT request. JwtAuthFilter gets this right (finally block,
 * with its own comment explaining why: "Thread goes back to pool after
 * this — MUST be clean"). This filter follows JwtAuthFilter's pattern,
 * not GuardJwtFilter's -- the latter is a pre-existing bug worth fixing
 * separately, not copying into new code.
 *
 * Explicit rejection responses (401/403 with a reason), matching
 * GuardJwtFilter's style — not JwtAuthFilter's fail-through-to-Spring's-
 * generic-403 pattern. An API consumer hitting a scope/read-only
 * violation benefits from knowing WHY, the same way a guard app benefits
 * from "Session has been revoked" instead of a bare 403.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_SCHEME = "ApiKey ";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final PublicApiService publicApiService;
    private final ObjectMapper     objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return authHeader == null || !authHeader.startsWith(API_KEY_SCHEME);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        try {
            String rawKey = request.getHeader("Authorization").substring(API_KEY_SCHEME.length()).trim();

            Optional<ApiKey> maybeKey = publicApiService.validateKey(rawKey);
            if (maybeKey.isEmpty()) {
                log.info("[ApiKeyAuthFilter] Invalid, expired, or revoked API key presented");
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired API key");
                return;
            }
            ApiKey apiKey = maybeKey.get();

            if (apiKey.isReadOnly() && !SAFE_METHODS.contains(request.getMethod())) {
                log.warn("[ApiKeyAuthFilter] Read-only key '{}' attempted {} {}",
                        apiKey.getName(), request.getMethod(), request.getRequestURI());
                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                        "This API key is read-only and cannot perform " + request.getMethod() + " requests");
                return;
            }

            if (!isPathInScope(apiKey, request.getRequestURI())) {
                log.warn("[ApiKeyAuthFilter] Key '{}' attempted out-of-scope path {}",
                        apiKey.getName(), request.getRequestURI());
                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                        "This API key is not scoped to access this endpoint");
                return;
            }

            // NOTE: apiKey.getBranchId() is NOT enforced here -- branch-level
            // scoping isn't wired anywhere in this module yet (same gap as
            // GuardController/SiteController/etc). Flagged, not silently
            // ignored -- see the branch-scoping conversation this filter
            // came out of for what's still needed.

            TenantContext.setTenantId(apiKey.getTenantId().getValue().toString());
            // The API key's own ID stands in for "acting user" -- there is no
            // human logged in for a machine caller, and attributing audit
            // trail entries (acknowledgedBy, resolvedBy, etc.) to the human
            // who originally CREATED the key would misrepresent who/what
            // actually triggered this specific request. This is a judgment
            // call, not an obvious one -- reconsider if audit trails need to
            // show the key's creator instead (apiKey.getCreatedBy()).
            TenantContext.setUserId(apiKey.getId().toString());
            TenantContext.setUserName("API Key: " + apiKey.getName());

            Set<SimpleGrantedAuthority> authorities = new HashSet<>();
            authorities.add(new SimpleGrantedAuthority("USER_READ"));
            if (!apiKey.isReadOnly()) {
                authorities.add(new SimpleGrantedAuthority("USER_UPDATE"));
            }

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            apiKey.getId().toString(), null, authorities));

            chain.doFilter(request, response);

        } catch (Exception e) {
            log.error("[ApiKeyAuthFilter] API key authentication failed: {}", e.getMessage(), e);
            if (!response.isCommitted()) {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "API key authentication failed");
            }
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * scopePrefixesJson: JSON array of allowed path prefixes, e.g.
     * ["/api/v1/security/reports", "/api/v1/security/sites"]. Null/blank =
     * full access to all security endpoints (per CreateApiKeyRequest's own
     * documented semantics). A non-null value that fails to parse is
     * treated as "no scope granted" (fail closed), not "full access" --
     * malformed data should never silently widen access.
     */
    private boolean isPathInScope(ApiKey apiKey, String requestUri) {
        String scopeJson = apiKey.getScopePrefixes();
        if (scopeJson == null || scopeJson.isBlank()) {
            return true;
        }
        try {
            List<String> prefixes = objectMapper.readValue(scopeJson, List.class);
            if (prefixes.isEmpty()) return true;
            return prefixes.stream().anyMatch(requestUri::startsWith);
        } catch (Exception e) {
            log.error("[ApiKeyAuthFilter] Malformed scopePrefixes for key '{}' — failing closed: {}",
                    apiKey.getName(), e.getMessage());
            return false;
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message.replace("\"", "'") + "\"}");
    }
}