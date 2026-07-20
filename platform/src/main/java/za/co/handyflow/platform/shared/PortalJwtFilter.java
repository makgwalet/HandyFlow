package za.co.handyflow.platform.shared;

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

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Closes the "client portal" gap's auth layer. Mirrors JwtAuthFilter's
 * exact structure — confirmed directly by reading the real
 * JwtAuthFilter.java, not guessed at.
 * <p>
 * shouldNotFilter() only lets this run on portal-namespaced paths,
 * mirroring how JwtAuthFilter itself exempts /api/v1/admin/**. Even if
 * it did run elsewhere, it would just find no valid portal token and
 * pass through harmlessly (it never sets authentication on a missing
 * or invalid token) — but scoping it explicitly avoids wasted
 * execution and keeps intent clear.
 * <p>
 * Deliberately does NOT populate TenantContext the way JwtAuthFilter
 * does for staff tokens — a portal user isn't tied to one tenant the
 * way a staff member is; which client/tenant a specific request is
 * about is resolved per-endpoint against the grants table instead. See
 * PortalJwtService's own class Javadoc for the full reasoning.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortalJwtFilter extends OncePerRequestFilter {

    private final PortalJwtService portalJwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains("/portal/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            final String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                final String jwt = authHeader.substring(7);

                if (portalJwtService.isTokenValid(jwt)) {
                    UUID portalUserId = portalJwtService.extractPortalUserId(jwt);

                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    portalUserId.toString(), null,
                                    Set.of(new SimpleGrantedAuthority("PORTAL_USER"))
                            )
                    );
                }
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Portal JWT authentication failed: {}", e.getMessage());
            if (!response.isCommitted()) {
                filterChain.doFilter(request, response);
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}