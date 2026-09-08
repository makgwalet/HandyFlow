package za.co.handyflow.platform;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.handyflow.platform.config.SecurityConfig;
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
 * FIX (403-vs-409 investigation): confirmed empirically via a throwaway
 * diagnostic (AopUtils.isAopProxy on the controller bean MockMvc actually
 * invokes) that this class's own doc comment above was WRONG about one
 * specific claim — "@WebMvcTest pulls in SecurityConfig" was describing
 * an assumption, not a verified mechanism, and this class never actually
 * @Imported SecurityConfig anywhere in its real code, only provided mock
 * beans for SecurityConfig's own dependencies. SecurityConfig's @Bean
 * methods clearly DO run somehow (confirmed by the NoSuchBeanDefinitionException
 * behavior documented above), so SecurityConfig itself IS being discovered
 * by @WebMvcTest's own auto-detection — but @EnableMethodSecurity's AOP
 * auto-proxying (which needs its own BeanPostProcessor registered before
 * ANY singleton bean, including the controller, is instantiated) was
 * empirically confirmed NOT to have applied: the controller bean under
 * test was a completely raw, unproxied instance
 * (isAopProxy/isCglibProxy/isJdkDynamicProxy all false), meaning
 * @PreAuthorize was structurally incapable of firing regardless of what
 * authority @WithMockUser granted. Explicit @Import here — rather than
 * relying on @WebMvcTest's own auto-detection timing — routes
 * SecurityConfig (and @EnableMethodSecurity's own nested
 * @Import(AutoProxyRegistrar.class) chain) through Spring's standard,
 * deterministic ConfigurationClassParser pipeline, which is guaranteed to
 * run before singleton bean instantiation. This is the most likely fix
 * given the empirical evidence, but I could not re-run the diagnostic
 * myself to confirm it actually resolves the unproxied-bean finding —
 * please re-run PreAuthorizeDiagnosticTest after applying this and
 * confirm isAopProxy flips to true and the wrong-authority request
 * returns 403 before deleting that diagnostic test.
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
@Import(SecurityConfig.class)
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