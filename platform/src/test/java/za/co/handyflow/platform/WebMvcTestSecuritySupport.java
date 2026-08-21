package za.co.handyflow.platform;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.security.application.internal.GuardAuthService;
import za.co.handyflow.platform.security.application.internal.PublicApiService;
import za.co.handyflow.platform.shared.IdempotencyKeyService;
import za.co.handyflow.platform.shared.JwtService;
import za.co.handyflow.platform.shared.PortalJwtService;

/**
 * Import this into any {@code @WebMvcTest(SomeController.class)} that needs
 * the real {@code SecurityConfig} to load (which is every one of them —
 * {@code @WebMvcTest} pulls in {@code SecurityConfig} specifically so
 * {@code @PreAuthorize} can be exercised in slice tests).
 * <p>
 * ROOT CAUSE this exists to fix: {@code SecurityConfig} is a single
 * {@code @Configuration} class that wires all seven of the platform's
 * custom auth filters — {@code RateLimitFilter}, {@code JwtAuthFilter},
 * {@code AdminJwtFilter}, {@code GuardJwtFilter}, {@code PortalJwtFilter},
 * {@code ApiKeyAuthFilter}, {@code IdempotencyKeyFilter} — into one
 * {@code SecurityFilterChain} bean. A {@code @WebMvcTest} slice loads
 * {@code SecurityConfig} (that's required for {@code @PreAuthorize} to be
 * testable at all), which means Spring must construct every one of those
 * seven filter beans, which means every one of THEIR dependencies —
 * {@code JwtService}, {@code GuardAuthService}, {@code PortalJwtService},
 * {@code PublicApiService}, {@code RateLimiter}, {@code IdempotencyKeyService}
 * — must resolve too, none of which are part of the web-layer slice.
 * Confirmed live: without this class, {@code @WebMvcTest(HrController.class)}
 * fails context startup with
 * {@code NoSuchBeanDefinitionException: JwtService} before a single test runs.
 * <p>
 * USAGE — add both of these to the test class:
 * <pre>{@code
 * @WebMvcTest(HrController.class)
 * @Import(WebMvcTestSecuritySupport.class)
 * class HrControllerTest { ... }
 * }</pre>
 * Nothing else needs to change — controllers under test still get real
 * {@code @PreAuthorize} enforcement via {@code @WithMockUser}, since method
 * security is AOP-based and independent of what these filters actually do.
 * These mocks only exist so the filter beans can be CONSTRUCTED; none of
 * their methods need stubbing because {@code @WithMockUser} sets
 * {@code SecurityContextHolder} directly, so the JWT-parsing filters never
 * have a reason to touch a real Authorization header in these tests.
 * <p>
 * NOT YET DONE: applying this import retroactively to existing
 * {@code @WebMvcTest} classes elsewhere in the suite (e.g.
 * {@code ClinicControllerTest}, which has the identical gap — confirmed by
 * inspection, not yet confirmed by running it) is out of scope for the
 * backlog item this class was built for. Flagging it here so it isn't lost.
 */
@TestConfiguration
public class WebMvcTestSecuritySupport {

    @Bean
    @Primary
    public JwtService jwtService() {
        return org.mockito.Mockito.mock(JwtService.class);
    }

    @Bean
    @Primary
    public GuardAuthService guardAuthService() {
        return org.mockito.Mockito.mock(GuardAuthService.class);
    }

    @Bean
    @Primary
    public PortalJwtService portalJwtService() {
        return org.mockito.Mockito.mock(PortalJwtService.class);
    }

    @Bean
    @Primary
    public PublicApiService publicApiService() {
        return org.mockito.Mockito.mock(PublicApiService.class);
    }

    @Bean
    @Primary
    public RateLimiter rateLimiter() {
        return org.mockito.Mockito.mock(RateLimiter.class);
    }

    @Bean
    @Primary
    public IdempotencyKeyService idempotencyKeyService() {
        return org.mockito.Mockito.mock(IdempotencyKeyService.class);
    }
}