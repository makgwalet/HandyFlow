package za.co.handyflow.platform.projects.application.internal.clientportal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectPortalGrant;
import za.co.handyflow.platform.projects.domain.repository.ProjectPortalGrantRepository;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.AuthResponse;
import za.co.handyflow.platform.projects.dto.ClientPortalDtos.InviteInfo;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Accepting a Projects invite and logging in, on the shared {@link PortalUser} identity (the same login as the other
 * client portals). Someone who already has a portal login must enter its password to accept, so an invite can never be
 * used to attach access to another person's account. Login is refused when the person has no active project access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPortalAuthService {

    private final PortalUserRepository portalUsers;
    private final ProjectPortalGrantRepository grants;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwt;

    @Transactional(readOnly = true)
    public InviteInfo inviteInfo(String token) {
        ProjectPortalGrant g = usable(token);
        return new InviteInfo(g.getClientName(), g.getInviteEmail(), g.getContactName(), g.getInvitedByName(),
                portalUsers.findByEmail(g.getInviteEmail()).isPresent(), g.getInviteExpiresAt());
    }

    @Transactional
    public AuthResponse register(String token, String password, String fullName) {
        ProjectPortalGrant g = usable(token);
        Optional<PortalUser> existing = portalUsers.findByEmail(g.getInviteEmail());
        PortalUser user;
        if (existing.isPresent()) {
            user = existing.get();
            if (!user.isActive()) throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
            if (!passwordEncoder.matches(password, user.getPasswordHash()))
                throw new HandyFlowException("An account already exists for this email. Enter its current password to accept this invite.",
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        } else {
            String name = fullName == null || fullName.isBlank()
                    ? (g.getContactName() == null || g.getContactName().isBlank() ? null : g.getContactName().trim()) : fullName.trim();
            if (name == null) throw new HandyFlowException("Enter your name", HttpStatus.BAD_REQUEST, "NAME_REQUIRED");
            user = portalUsers.save(PortalUser.create(g.getInviteEmail(), passwordEncoder.encode(password), name));
        }
        g.accept(user.getId());
        grants.save(g);
        user.recordLogin();
        portalUsers.save(user);
        log.info("Projects portal invite accepted grant={} user={}", g.getId(), user.getId());
        return new AuthResponse(portalJwt.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        PortalUser user = portalUsers.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!passwordEncoder.matches(password, user.getPasswordHash()))
            throw new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        if (!user.isActive()) throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        if (grants.findActiveForUser(user.getId()).isEmpty()) throw ClientPortalAccess.noAccess();
        user.recordLogin();
        portalUsers.save(user);
        return new AuthResponse(portalJwt.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    /** Unknown, malformed, used, revoked and expired links all give the same answer. */
    private ProjectPortalGrant usable(String token) {
        if (!ClientPortalTokens.wellFormed(token)) throw invalid();
        return grants.findByInviteTokenHash(ClientPortalTokens.hash(token))
                .filter(g -> g.inviteUsable(Instant.now()))
                .orElseThrow(ClientPortalAuthService::invalid);
    }

    private static HandyFlowException invalid() {
        return new HandyFlowException("This invite link is not valid. It may have expired or already been used; ask for a new one.",
                HttpStatus.BAD_REQUEST, "INVITE_INVALID");
    }
}
