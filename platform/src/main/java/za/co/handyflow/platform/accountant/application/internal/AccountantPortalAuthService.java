package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.AccPortalAccessGrant;
import za.co.handyflow.platform.accountant.domain.repository.AccPortalAccessGrantRepository;
import za.co.handyflow.platform.accountant.dto.PortalAccessGrantResponse;
import za.co.handyflow.platform.accountant.dto.PortalAuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

import java.util.UUID;

/**
 * Closes the "client portal" gap's auth layer. Deliberately named
 * AccountantPortalAuthService, not a generic "PortalAuthService" — this
 * handles accepting invites TO THIS MODULE specifically, even though it
 * uses the shared PortalUser identity underneath. A future module (e.g.
 * Recruiter) would build its own small equivalent service for its own
 * grants table, rather than this growing into one increasingly complex
 * "god service" for every module's invite flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountantPortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final AccPortalAccessGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    /**
     * First-time registration via a fresh invite token. If a PortalUser
     * already exists for this email — they've registered via a
     * different invite already, for another client or (in the future)
     * another module — this deliberately REJECTS rather than silently
     * creating a second identity or overwriting the existing one. See
     * acceptAdditionalInvite() for the correct flow when someone is
     * already logged in.
     */
    @Transactional
    public PortalAuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        AccPortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
                .orElseThrow(() -> new HandyFlowException(
                        "This invite link is invalid", HttpStatus.BAD_REQUEST, "INVALID_INVITE"));

        if (!grant.isInviteValid()) {
            throw new HandyFlowException(
                    "This invite link has expired or has already been used",
                    HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        }

        if (portalUserRepo.findByEmail(grant.getInviteEmail()).isPresent()) {
            throw new HandyFlowException(
                    "An account already exists for this email — please log in instead",
                    HttpStatus.CONFLICT, "ACCOUNT_EXISTS");
        }

        PortalUser user = PortalUser.create(grant.getInviteEmail(), passwordEncoder.encode(password), fullName);
        portalUserRepo.save(user);

        grant.acceptInvite(user.getId());
        grantRepo.save(grant);

        String token = portalJwtService.generateToken(user.getId(), user.getEmail());
        log.info("Portal user registered via invite: {}", user.getEmail());
        return new PortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    /**
     * A DIFFERENT flow from registerViaInvite() — for a portal user
     * who's already logged in (e.g. from accepting a previous client's
     * invite) and is now accepting a SECOND invite for another client.
     * No password needed; their existing identity is simply linked to
     * the new grant. Requires the invite's target email to match the
     * currently logged-in user's email — accepting an invite meant for
     * a different address should register a new identity via
     * registerViaInvite() instead, not silently attach to an unrelated
     * account.
     */
    @Transactional
    public PortalAccessGrantResponse acceptAdditionalInvite(UUID currentPortalUserId, String inviteToken) {
        AccPortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
                .orElseThrow(() -> new HandyFlowException(
                        "This invite link is invalid", HttpStatus.BAD_REQUEST, "INVALID_INVITE"));

        if (!grant.isInviteValid()) {
            throw new HandyFlowException(
                    "This invite link has expired or has already been used",
                    HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        }

        PortalUser user = portalUserRepo.findById(currentPortalUserId)
                .orElseThrow(() -> new HandyFlowException(
                        "Portal session invalid", HttpStatus.UNAUTHORIZED, "INVALID_SESSION"));

        if (!user.getEmail().equalsIgnoreCase(grant.getInviteEmail())) {
            throw new HandyFlowException(
                    "This invite was sent to a different email address than your logged-in account",
                    HttpStatus.FORBIDDEN, "EMAIL_MISMATCH");
        }

        grant.acceptInvite(user.getId());
        grantRepo.save(grant);
        log.info("Additional portal invite accepted: {} → client={}", user.getEmail(), grant.getClientId());
        return new PortalAccessGrantResponse(grant.getId(), grant.getInviteEmail(), grant.getStatus(),
                grant.getInvitedAt(), grant.getAcceptedAt(), grant.getRevokedAt());
    }

    @Transactional
    public PortalAuthResponse login(String email, String password) {
        PortalUser user = portalUserRepo.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        if (!user.isActive()) {
            throw new HandyFlowException(
                    "This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new HandyFlowException(
                    "Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        user.recordLogin();
        portalUserRepo.save(user);

        String token = portalJwtService.generateToken(user.getId(), user.getEmail());
        log.info("Portal login: {}", user.getEmail());
        return new PortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }
}