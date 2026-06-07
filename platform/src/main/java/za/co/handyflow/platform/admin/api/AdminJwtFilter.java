package za.co.handyflow.platform.admin.api;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.handyflow.platform.shared.JwtService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminJwtFilter extends OncePerRequestFilter {

    // WHY reuse JwtService? It already holds the secret and parser.
    // AdminJwtFilter only adds extra validation: role=SUPERADMIN + state=AUTHENTICATED.
    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/admin/") ||
                path.equals("/api/v1/admin/auth/login") ||
                path.equals("/api/v1/admin/auth/verify-totp") ||
                path.startsWith("/api/v1/admin/auth/totp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Admin token required");
            return;
        }

        String token = header.substring(7);
        try {
            if (!jwtService.isTokenValid(token)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid admin token");
                return;
            }

            // Extract claims via JwtService
            Claims claims = jwtService.extractAllClaims(token);
            String role  = (String) claims.get("role");
            String state = (String) claims.get("state");

            if (!"SUPERADMIN".equals(role) || !"AUTHENTICATED".equals(state)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Valid admin token required — tenant tokens not accepted here");
                return;
            }

            // Phase 1 fix: principal = adminId (UUID string) so getAdminId()
            // works via authentication.getPrincipal(). Email stored as Details
            // so extractAdminEmailFromContext() can retrieve it without re-parsing JWT.
            String adminId    = claims.getSubject();
            String adminEmail = (String) claims.get("email");
            String fullName   = (String) claims.get("fullName");

            var auth = new UsernamePasswordAuthenticationToken(
                    adminId, null,
                    List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN")));
            // Store email + fullName as WebAuthenticationDetails-compatible map
            auth.setDetails(java.util.Map.of(
                    "email",    adminEmail != null ? adminEmail : "",
                    "fullName", fullName   != null ? fullName   : ""
            ));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            log.warn("Admin JWT validation failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired admin token");
            return;
        }

        chain.doFilter(request, response);
    }
}
