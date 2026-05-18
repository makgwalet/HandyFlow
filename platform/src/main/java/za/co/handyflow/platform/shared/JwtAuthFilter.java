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

                if (jwtService.isTokenValid(jwt)) {
                    String userId   = jwtService.extractUserId(jwt);
                    String tenantId = jwtService.extractTenantId(jwt);
                    Set<String> permissions = jwtService.extractPermissions(jwt);

                    TenantContext.setTenantId(tenantId);

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
