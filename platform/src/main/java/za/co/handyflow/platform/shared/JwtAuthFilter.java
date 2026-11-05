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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    /** Optional so the filter still builds in tests that do not wire it. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TokenVersions tokenVersions;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/admin/");
    }

    /** Real tenant tokens only: admin and impersonation tokens carry no tenant version. */
    private boolean staleForTenant(String jwt) {
        if (tokenVersions == null || jwtService.isImpersonation(jwt)) return false;
        try {
            return tokenVersions.rejects(java.util.UUID.fromString(jwtService.extractTenantId(jwt)),
                    jwtService.extractTokenVersion(jwt));
        } catch (Exception e) {
            return false;
        }
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

                if (jwtService.isTokenValid(jwt) && !staleForTenant(jwt)) {
                    String userId   = jwtService.extractUserId(jwt);
                    String tenantId = jwtService.extractTenantId(jwt);
                    Set<String> permissions = jwtService.extractPermissions(jwt);
                    boolean impersonation = jwtService.isImpersonation(jwt);

                    TenantContext.setTenantId(tenantId);
                    TenantContext.setUserId(userId);
                    TenantContext.setImpersonation(impersonation);
                    // BUG-004 fix: populate USER_NAME so getCurrentUserName()
                    // returns a real display name, not the UUID fallback.
                    String displayName = jwtService.extractName(jwt);
                    TenantContext.setUserName(displayName);

                    var authorities = permissions.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toSet());

                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    userId, null, authorities
                            )
                    );
                }
            }

            // WHY here — not in finally?
            // This call runs the REST of the filter chain including the controller.
            // TenantContext must already be set when this runs.
            // After this line returns, the response is already written —
            // all we need to do is clean up.
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            // Only call doFilter if it hasn't been called yet
            if (!response.isCommitted()) {
                filterChain.doFilter(request, response);
            }
        } finally {
            // WHY finally? Always runs — even on exception.
            // Thread goes back to pool after this — MUST be clean.
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}


