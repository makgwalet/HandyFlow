package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPortalAccessGrant;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmPortalAccessGrantRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalAuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

import java.util.Optional;

/**
 * Handles accepting invites TO THIS MODULE specifically, using the shared
 * {@link PortalUser} identity underneath.
 * <p>
 * Chunk 2 change: {@code PortalUser} is a shared identity, so a person
 * invited by a second client or tenant already has an account. Previously
 * that invite always failed with ACCOUNT_EXISTS and could never be
 * accepted. Now, if an account exists for the invited email, the invitee
 * must supply that account's existing password, and the grant is linked to
 * it — proving ownership of the existing identity, so an invite can't be
 * used to attach access to someone else's account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmPortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final FmPortalAccessGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    @Transactional
    public FmPortalAuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        FmPortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
                .orElseThrow(() -> new HandyFlowException(
                        "This invite link is invalid", HttpStatus.BAD_REQUEST, "INVALID_INVITE"));

        if (!grant.isInviteValid()) {
            throw new HandyFlowException(
                    "This invite link has expired or has already been used",
                    HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        }

        Optional<PortalUser> existing = portalUserRepo.findByEmail(grant.getInviteEmail());
        PortalUser user;
        if (existing.isPresent()) {
            user = existing.get();
            if (!user.isActive()) {
                throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new HandyFlowException(
                        "An account already exists for this email — enter your existing password to accept this invite",
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
            }
            log.info("FM portal invite accepted by existing portal user: {}", user.getEmail());
        } else {
            user = PortalUser.create(grant.getInviteEmail(), passwordEncoder.encode(password), fullName);
            portalUserRepo.save(user);
            log.info("FM portal user registered via invite: {}", user.getEmail());
        }

        grant.acceptInvite(user.getId());
        grantRepo.save(grant);

        String token = portalJwtService.generateToken(user.getId(), user.getEmail());
        return new FmPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public FmPortalAuthResponse login(String email, String password) {
        PortalUser user = portalUserRepo.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));

        if (!user.isActive()) {
            throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }

        user.recordLogin();
        portalUserRepo.save(user);

        String token = portalJwtService.generateToken(user.getId(), user.getEmail());
        log.info("FM portal login: {}", user.getEmail());
        return new FmPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }
}