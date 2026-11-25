package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.CompliancePortalGrant;
import za.co.handyflow.platform.complianceservices.domain.repository.CompliancePortalGrantRepository;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.AuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Accepting an invite and signing in, on the shared portal identity. A person who already has a portal login (from
 * another module or another practice) proves it with their existing password before the invite is attached to it, so
 * an invite cannot be used to attach access to someone else's account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompliancePortalAuthService {

    private final PortalUserRepository portalUsers;
    private final CompliancePortalGrantRepository grants;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService jwt;

    @Transactional
    public AuthResponse register(String inviteToken, String password, String fullName) {
        CompliancePortalGrant grant = grants.findByInviteToken(inviteToken)
                .orElseThrow(() -> new HandyFlowException("This invite link is invalid", HttpStatus.BAD_REQUEST, "INVALID_INVITE"));
        if (!grant.isInviteValid(Instant.now())) {
            throw new HandyFlowException("This invite link has expired or has already been used", HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        }
        Optional<PortalUser> existing = portalUsers.findByEmail(grant.getInviteEmail());
        PortalUser user;
        if (existing.isPresent()) {
            user = existing.get();
            if (!user.isActive()) throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new HandyFlowException("An account already exists for this email. Enter your existing password to accept this invite", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            }
        } else {
            user = portalUsers.save(PortalUser.create(grant.getInviteEmail(), passwordEncoder.encode(password), fullName.trim()));
        }
        try {
            grant.accept(user.getId(), Instant.now());
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        }
        grants.save(grant);
        log.info("Compliance portal invite accepted by {}", user.getEmail());
        return new AuthResponse(jwt.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        PortalUser user = portalUsers.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!user.isActive()) throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        if (!passwordEncoder.matches(password, user.getPasswordHash())) throw new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        user.recordLogin();
        portalUsers.save(user);
        return new AuthResponse(jwt.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }
}
