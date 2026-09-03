package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.co.handyflow.platform.billing.api.SubscriptionResponse;
import za.co.handyflow.platform.billing.application.SubscriptionQueryFacade;
import za.co.handyflow.platform.identity.domain.model.Role;
import za.co.handyflow.platform.identity.domain.model.Tenant;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.TenantRepository;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.identity.dto.request.LoginRequest;
import za.co.handyflow.platform.identity.dto.request.RegisterRequest;
import za.co.handyflow.platform.identity.dto.response.AuthResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.JwtService;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for AuthService — no Spring context, all dependencies
 * mocked with Mockito, matching this codebase's own established
 * convention (see ClinicServiceTest, PayrollEngineParityTest).
 * <p>
 * Written as part of the identity module modernization pass: this
 * module previously had ZERO backend unit tests despite being the
 * security/tenancy foundation every other module depends on (confirmed:
 * 97 test files existed across the whole repo, none under
 * za.co.handyflow.platform.identity). These tests cover the two
 * highest-risk flows in this class — register() and login() — including
 * regression coverage for the disposable-email check and the
 * subscription-status resolution documented inline in AuthService
 * itself.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock UserRepository userRepository;
    @Mock SubscriptionQueryFacade subscriptionQueryFacade;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RoleService roleService;
    @Mock EmailService emailService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock DisposableEmailChecker disposableEmailChecker;

    private AuthService service;

    private static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");

    // AuthServiceTest constructs the service manually (rather than
    // @InjectMocks) so field order/ordering changes in AuthService don't
    // silently misassign mocks — RequiredArgsConstructor field order is
    // fixed by AuthService's own declaration order, confirmed directly
    // against that file.
    private AuthService newService() {
        return new AuthService(
                tenantRepository, userRepository, subscriptionQueryFacade,
                passwordEncoder, jwtService, roleService, emailService,
                emailVerificationService, disposableEmailChecker);
    }

    private static RegisterRequest registerRequest(String email) {
        return new RegisterRequest(
                "Zeta Earthmoving", "zeta-earthmoving", "Thabo", "Nkosi",
                email, "SecurePass123", "0115550100", "construction", null,
                List.of("fleet"));
    }

    private static Role adminRole(TenantId tenantId) {
        return Role.create(tenantId, "ADMIN", "Full system administrator");
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("rejects disposable email domains before any DB check")
        void rejectsDisposableEmail() {
            service = newService();
            RegisterRequest req = registerRequest("owner@mailinator.com");
            when(disposableEmailChecker.isDisposable(req.email())).thenReturn(true);

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disposable");

            // FIX regression: disposable check must run BEFORE any
            // uniqueness query — the cheapest possible rejection path,
            // per AuthService.register()'s own comment.
            verifyNoInteractions(tenantRepository);
        }

        @Test
        @DisplayName("rejects a slug that is already taken")
        void rejectsDuplicateSlug() {
            service = newService();
            RegisterRequest req = registerRequest("owner@zeta.co.za");
            when(disposableEmailChecker.isDisposable(anyString())).thenReturn(false);
            when(tenantRepository.existsBySlug(req.slug())).thenReturn(true);

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already taken");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects an email already registered to another tenant")
        void rejectsDuplicateEmail() {
            service = newService();
            RegisterRequest req = registerRequest("owner@zeta.co.za");
            when(disposableEmailChecker.isDisposable(anyString())).thenReturn(false);
            when(tenantRepository.existsBySlug(req.slug())).thenReturn(false);
            when(tenantRepository.existsByEmail(req.email())).thenReturn(true);

            assertThatThrownBy(() -> service.register(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("creates tenant and owner, assigns the default admin role, and never throws on email failure")
        void happyPathSurvivesEmailFailure() {
            service = newService();
            RegisterRequest req = registerRequest("owner@zeta.co.za");
            when(disposableEmailChecker.isDisposable(anyString())).thenReturn(false);
            when(tenantRepository.existsBySlug(req.slug())).thenReturn(false);
            when(tenantRepository.existsByEmail(req.email())).thenReturn(false);
            when(passwordEncoder.encode(req.password())).thenReturn("hashed");
            when(jwtService.generateToken(any(), any(), any(), any(), any(), any())).thenReturn("token");
            when(jwtService.getExpirationSeconds()).thenReturn(86400L);
            when(emailVerificationService.createToken(any(), any())).thenReturn("verify-token");
            // Registration must succeed even when the welcome email
            // fails to send — AuthService.register()'s own try/catch is
            // the thing under test here.
            doThrow(new RuntimeException("SMTP down")).when(emailService)
                    .send(anyString(), anyString(), anyString());

            ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
            when(roleService.createDefaultAdminRole(any())).thenAnswer(inv ->
                    adminRole(inv.getArgument(0)));
            when(subscriptionQueryFacade.getSubscription(any()))
                    .thenReturn(subscription("PILOT"));

            AuthResponse response = service.register(req);

            verify(tenantRepository).save(tenantCaptor.capture());
            Tenant saved = tenantCaptor.getValue();
            assertThat(saved.getName()).isEqualTo("Zeta Earthmoving");
            assertThat(saved.getSlug()).isEqualTo("zeta-earthmoving");
            assertThat(saved.getStatus()).isEqualTo(Tenant.TenantStatus.TRIAL);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedOwner = userCaptor.getValue();
            assertThat(savedOwner.getEmail()).isEqualTo("owner@zeta.co.za");
            assertThat(savedOwner.getRoles()).extracting(Role::getName).containsExactly("ADMIN");

            assertThat(response.accessToken()).isEqualTo("token");
            assertThat(response.subscriptionStatus()).isEqualTo("PILOT");
        }

        @Test
        @DisplayName("subscription lookup failure never blocks registration — subscriptionStatus is null instead")
        void subscriptionLookupFailureIsNonFatal() {
            service = newService();
            RegisterRequest req = registerRequest("owner@zeta.co.za");
            when(disposableEmailChecker.isDisposable(anyString())).thenReturn(false);
            when(tenantRepository.existsBySlug(req.slug())).thenReturn(false);
            when(tenantRepository.existsByEmail(req.email())).thenReturn(false);
            when(passwordEncoder.encode(req.password())).thenReturn("hashed");
            when(jwtService.generateToken(any(), any(), any(), any(), any(), any())).thenReturn("token");
            when(jwtService.getExpirationSeconds()).thenReturn(86400L);
            when(emailVerificationService.createToken(any(), any())).thenReturn("verify-token");
            when(roleService.createDefaultAdminRole(any())).thenAnswer(inv -> adminRole(inv.getArgument(0)));
            when(subscriptionQueryFacade.getSubscription(any()))
                    .thenThrow(new RuntimeException("billing service unavailable"));

            AuthResponse response = service.register(req);

            assertThat(response.subscriptionStatus()).isNull();
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        private final LoginRequest req = new LoginRequest("owner@zeta.co.za", "SecurePass123", "zeta-earthmoving");

        @Test
        @DisplayName("rejects an unknown tenant slug")
        void rejectsUnknownSlug() {
            service = newService();
            when(tenantRepository.findBySlug("zeta-earthmoving")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No company found");
        }

        @Test
        @DisplayName("rejects login for a suspended or cancelled tenant before checking credentials")
        void rejectsSuspendedTenant() {
            service = newService();
            Tenant suspended = registeredTenant(Tenant.TenantStatus.SUSPENDED);
            when(tenantRepository.findBySlug("zeta-earthmoving")).thenReturn(Optional.of(suspended));

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("suspended or cancelled");

            // Credentials must never be checked once the tenant itself
            // is known to be locked out — nothing to gain from a
            // password comparison at that point.
            verifyNoInteractions(userRepository, passwordEncoder);
        }

        @Test
        @DisplayName("rejects an unknown email with a generic message (no user enumeration)")
        void rejectsUnknownEmailGenerically() {
            service = newService();
            Tenant tenant = registeredTenant(Tenant.TenantStatus.ACTIVE);
            when(tenantRepository.findBySlug("zeta-earthmoving")).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmailAndTenantId(req.email(), tenant.getTenantId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("rejects an incorrect password with the same generic message as unknown email")
        void rejectsWrongPasswordGenerically() {
            service = newService();
            Tenant tenant = registeredTenant(Tenant.TenantStatus.ACTIVE);
            User user = User.create(tenant.getTenantId(), req.email(), "hashed", "Thabo", "Nkosi");
            when(tenantRepository.findBySlug("zeta-earthmoving")).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmailAndTenantId(req.email(), tenant.getTenantId()))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(req.password(), "hashed")).thenReturn(false);

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("rejects a deactivated user even with the correct password")
        void rejectsDeactivatedUser() {
            service = newService();
            Tenant tenant = registeredTenant(Tenant.TenantStatus.ACTIVE);
            User user = User.create(tenant.getTenantId(), req.email(), "hashed", "Thabo", "Nkosi");
            user.deactivate();
            when(tenantRepository.findBySlug("zeta-earthmoving")).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmailAndTenantId(req.email(), tenant.getTenantId()))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(req.password(), "hashed")).thenReturn(true);

            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("deactivated");
        }

        @Test
        @DisplayName("returns a token and the real subscription status on success")
        void succeedsAndReturnsRealSubscriptionStatus() {
            service = newService();
            Tenant tenant = registeredTenant(Tenant.TenantStatus.TRIAL);
            User user = User.create(tenant.getTenantId(), req.email(), "hashed", "Thabo", "Nkosi");
            when(tenantRepository.findBySlug("zeta-earthmoving")).thenReturn(Optional.of(tenant));
            when(userRepository.findByEmailAndTenantId(req.email(), tenant.getTenantId()))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(req.password(), "hashed")).thenReturn(true);
            when(jwtService.generateToken(any(), any(), any(), any(), any(), any())).thenReturn("token");
            when(jwtService.getExpirationSeconds()).thenReturn(86400L);
            // FIX regression: subscriptionStatus must come from
            // SubscriptionQueryFacade (which can report PAST_DUE), not
            // Tenant.status (which cannot) — see AuthService's own
            // comment on exactly this point.
            when(subscriptionQueryFacade.getSubscription(tenant.getTenantId()))
                    .thenReturn(subscription("PAST_DUE"));

            AuthResponse response = service.login(req);

            assertThat(response.accessToken()).isEqualTo("token");
            assertThat(response.expiresIn()).isEqualTo(86400L);
            assertThat(response.subscriptionStatus()).isEqualTo("PAST_DUE");
        }
    }

    private static Tenant registeredTenant(Tenant.TenantStatus status) {
        Tenant tenant = Tenant.register("Zeta Earthmoving", "zeta-earthmoving",
                "owner@zeta.co.za", "0115550100", "construction", null, List.of("fleet"));
        if (status == Tenant.TenantStatus.ACTIVE) tenant.activate();
        if (status == Tenant.TenantStatus.SUSPENDED) tenant.suspend();
        // TRIAL is Tenant.register()'s own default — nothing to do.
        return tenant;
    }

    private static SubscriptionResponse subscription(String status) {
        return new SubscriptionResponse(UUID.randomUUID(), "starter", "Starter",
                status, null, null, null, 0, null, null, "SUSPENDED".equals(status));
    }
}
