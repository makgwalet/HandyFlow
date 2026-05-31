package za.co.handyflow.platform.shared;

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

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // WHY? Enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AdminJwtFilter adminJwtFilter;

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
                                "/webjars/**",           // ← SpringDoc needs this
                                "/swagger-resources/**"  // ← SpringDoc needs this
                        ).permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // Insert our JWT filter BEFORE Spring's default auth filter
                .addFilterBefore(adminJwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
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
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        return new UrlBasedCorsConfigurationSource() {{
            registerCorsConfiguration("/**", config);
        }};
    }


}


