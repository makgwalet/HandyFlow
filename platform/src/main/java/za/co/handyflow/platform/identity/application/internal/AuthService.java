package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.Role;
import za.co.handyflow.platform.identity.domain.model.Tenant;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.TenantRepository;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.identity.dto.request.LoginRequest;
import za.co.handyflow.platform.identity.dto.request.RegisterRequest;
import za.co.handyflow.platform.identity.dto.response.AuthResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.JwtService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RoleService roleService;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (tenantRepository.existsBySlug(request.slug())) {
            throw new IllegalArgumentException(
                    "Slug '" + request.slug() + "' is already taken"
            );
        }

        if (tenantRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "A company is already registered with this email"
            );
        }

        // moduleKeys passed via event — billing listens and activates modules
        Tenant tenant = Tenant.register(
                request.companyName(),
                request.slug(),
                request.email(),
                request.phone(),
                request.businessType(),
                request.promoCode(),
                request.moduleKeys()
        );
        tenantRepository.save(tenant);

        Role adminRole = roleService.createDefaultAdminRole(tenant.getTenantId());

        User owner = User.create(
                tenant.getTenantId(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName()
        );
        owner.assignRole(adminRole);
        userRepository.save(owner);

        log.info("Registered new tenant={} owner={}", tenant.getSlug(), owner.getId());

        // NEW: previously nothing sent here at all — confirmed zero
        // emails fired anywhere in this method. A failed send must never
        // block registration itself, which is why the account is already
        // fully created and saved above before this runs.
        //
        // Token created first specifically so the welcome email below
        // can carry the real verify link — merged into the same email
        // rather than a separate second one (see EmailTemplates.
        // registrationConfirmation()'s own comment for why).
        try {
            String verifyToken = emailVerificationService.createToken(owner.getId(), tenant.getId());
            String verifyLink = "https://app.handyflow.co.za/verify-email?token=" + verifyToken;
            emailService.send(owner.getEmail(),
                    "Welcome to HandyFlow — your account is ready",
                    EmailTemplates.registrationConfirmation(
                            owner.getFirstName(), tenant.getName(), tenant.getSlug(),
                            request.moduleKeys(), verifyLink));
        } catch (Exception e) {
            log.error("Failed to send registration confirmation to={}: {}", owner.getEmail(), e.getMessage());
        }

        return buildAuthResponse(owner, tenant);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        Tenant tenant = tenantRepository.findBySlug(request.tenantSlug())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No company found with slug: " + request.tenantSlug()
                ));

        if (!tenant.isActive()) {
            throw new IllegalStateException("This account is suspended or cancelled");
        }

        User user = userRepository
                .findByEmailAndTenantId(request.email(), tenant.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid email or password"
                ));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new IllegalStateException("Your account has been deactivated");
        }

        log.info("User logged in: userId={} tenantId={}", user.getId(), tenant.getId());

        return buildAuthResponse(user, tenant);
    }

    private AuthResponse buildAuthResponse(User user, Tenant tenant) {
        String token = jwtService.generateToken(
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getFirstName(), user.getLastName(),
                user.getPermissionNames()
        );

        // FIX: expiresIn was hardcoded to 86400L, completely independent
        // of JwtService's own configured app.security.jwt.expiration-ms —
        // the two could silently drift apart with no compile-time or
        // runtime signal. Now derived from the same value the token
        // itself is actually signed with.
        //
        // FIX: subscriptionStatus previously didn't exist on this record
        // at all — LoginPage.tsx's redirect to AccountLockedPage could
        // never fire since it was always checking undefined. tenant is
        // already available right here, so this needs no new query.
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPermissionNames(),
                tenant.getStatus().name()
        );
    }
}