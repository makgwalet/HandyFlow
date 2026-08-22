package za.co.handyflow.platform.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import za.co.handyflow.platform.admin.api.AdminJwtFilter;
import za.co.handyflow.platform.security.infrastructure.ApiKeyAuthFilter;
import za.co.handyflow.platform.security.infrastructure.GuardJwtFilter;
import za.co.handyflow.platform.shared.JwtAuthFilter;
import za.co.handyflow.platform.shared.PortalJwtFilter;

import java.util.List;

/**
 * SecurityConfig — CHANGE: registered ApiKeyAuthFilter, closing the "API
 * keys exist in the DB but won't authenticate anything" gap flagged in
 * PublicApiController's own deployment note. No new permitAll() entry —
 * see ApiKeyAuthFilter's class javadoc for why a valid API key doesn't
 * need one (it's a real credential, authenticated by the filter itself,
 * same posture as GuardJwtFilter for /api/v1/guard/**). Everything else
 * below is unchanged from the original.
 * <p>
 * FIX: backlog 3.4 — added "/api/v1/hr/portal/auth/**" to permitAll().
 * Without it, the entire employee self-service portal was unreachable:
 * /auth/register and /auth/login both fell through to
 * .anyRequest().authenticated() and rejected every request with 401/403
 * before HrEmployeePortalAuthService ever ran, since there's no JWT to
 * present at registration/login time by definition. Confirmed missing on
 * two separate direct inspections before this fix.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // WHY? Enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final AdminJwtFilter adminJwtFilter;
    private final GuardJwtFilter guardJwtFilter;
    // NEW: closes the "client portal" gap. No import needed —
    // PortalJwtFilter lives in this same shared package.
    private final PortalJwtFilter portalJwtFilter;
    // NEW: closes the "API keys exist in the DB but won't authenticate"
    // gap flagged in PublicApiController's own deployment note.
    private final ApiKeyAuthFilter apiKeyAuthFilter;
    private final IdempotencyKeyFilter idempotencyKeyFilter;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                // WHY disable CSRF?
                // CSRF attacks target session-based auth (cookies).
                // We use stateless JWT — no sessions, so CSRF doesn't apply.
                .csrf(AbstractHttpConfigurer::disable)

                // WHY STATELESS?
                // JWT is self-contained — server doesn't store session state.
                // This is what makes our API scalable — any server instance
                // can validate any request without shared session storage.
                .sessionManagement( session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no JWT required
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/actuator/health",
                                "/actuator/info",
                                // WHY these paths? SpringDoc serves UI at /swagger-ui
                                // and the OpenAPI JSON spec at /v3/api-docs
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/api/v1/creative/approve/**",   // public proof approval — no login required
                                "/api/v1/desk/portal/**",
                                "/api/v1/marketing/unsubscribe/**",
                                "/api/v1/recruiter/careers/**",
                                "/api/v1/recruiter/portal/**",
                                "/api/v1/admin/auth/login",
                                "/api/v1/admin/auth/verify-totp",
                                "/api/v1/admin/auth/totp/setup",
                                "/api/v1/admin/auth/totp/confirm",
                                "/api/v1/portal/**",
                                // NEW: closes the "client portal" gap —
                                // only /auth/** is public (register,
                                // login). Everything else under
                                // /api/v1/accountant/portal/** falls
                                // through to .anyRequest().authenticated(),
                                // satisfied by portalJwtFilter below.
                                "/api/v1/accountant/portal/auth/**",
                                "/api/v1/auth/guard/**",   // guard login — no tenant JWT required
                                "/api/v1/security/cameras/motion-webhook",   // camera webhook — auth via webhookSecret, not JWT
                                "/webjars/**",           // ← SpringDoc needs this
                                "/swagger-resources/**",  // ← SpringDoc needs this
                                "/api/v1/payroll-bureau/portal/auth/**",
                                // FIX: backlog 3.4 — employee self-service
                                // portal. Same convention as every other
                                // portal in this list: only /auth/** is
                                // public; everything else under
                                // /api/v1/hr/portal/** falls through to
                                // .anyRequest().authenticated(), satisfied
                                // by portalJwtFilter (already registered
                                // below, already generic — matches any
                                // path containing "/portal/", confirmed
                                // directly from that filter's own
                                // shouldNotFilter()).
                                "/api/v1/hr/portal/auth/**"
                        ).permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Insert our JWT filter BEFORE Spring's default auth filter
                .addFilterBefore(adminJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(guardJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(portalJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, AdminJwtFilter.class)   // CHANGED: anchored
                // to a specific
                // custom filter,
                // not shared with
                // the others
                .addFilterAfter(idempotencyKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // WHY BCrypt?
        // BCrypt automatically salts passwords — same password hashes differently
        // each time. This defeats rainbow table attacks.
        // Strength 12 = 2^12 iterations — slow enough to deter brute force,
        // fast enough for real users (~300ms per hash).
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173",
                "http://localhost:5174" ));
        // FIX: PATCH was missing from this list. This is the actual root
        // cause behind every "PATCH triggers a CORS failure, switch to PUT"
        // workaround that's been found and reverted across this codebase
        // (earthmoving, fleet, and possibly others not yet audited) — none
        // of those workarounds could ever have fixed the real problem,
        // because the real problem was always here: the browser's preflight
        // OPTIONS request checks this exact list before the real PATCH
        // request is ever sent, and PATCH was never on it.
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        return new UrlBasedCorsConfigurationSource() {{
            registerCorsConfiguration("/**", config);
        }};
    }


}