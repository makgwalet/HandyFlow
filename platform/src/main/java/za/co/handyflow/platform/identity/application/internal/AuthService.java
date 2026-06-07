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

        return new AuthResponse(
                token,
                "Bearer",
                86400L,
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPermissionNames()
        );
    }
}