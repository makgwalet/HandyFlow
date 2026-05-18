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
import za.co.handyflow.platform.shared.TenantContext;

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

        // Guard: check uniqueness before creating anything
        if(tenantRepository.existsBySlug(request.slug())) {
            throw new IllegalArgumentException(
                    "Slug '" + request.slug() + "' is already taken"
            );
        }

        if (tenantRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "A company is already registered with this email"
            );
        }

        // 1. Create the tenant
        Tenant tenant = Tenant.register(
                request.companyName(),
                request.slug(),
                request.email()
        );
        tenantRepository.save(tenant);
        // WHY save tenant first?
        // User has a FK to tenant. Tenant must exist in DB before user is created.

        // 2. Create default ADMIN role for this tenant
        Role adminRole = roleService.createDefaultAdminRole(tenant.getTenantId());

        // 3. Create the owner user
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

        // 4. Generate JWT and return
        return buildAuthResponse(owner, tenant);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        // 1. Find the tenant by slug
        Tenant tenant = tenantRepository.findBySlug(request.tenantSlug())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No company found with slug: " + request.tenantSlug()
                ));

        if (!tenant.isActive()){
            throw new IllegalStateException("This account is suspended or cancelled");
        }

        // 2. Find the user within that tenant
        User user = userRepository
                .findByEmailAndTenantId(request.email(), tenant.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid email or password"
                        // WHY vague message? Never tell attackers which part was wrong.
                        // "Email not found" tells them the email doesn't exist — useful to attackers.
                ));

        // 3. Verify password
        if(!passwordEncoder.matches(request.password(), user.getPasswordHash())){
            throw new IllegalArgumentException("Invalid email or password");
        }

        if(!user.isActive()){
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
