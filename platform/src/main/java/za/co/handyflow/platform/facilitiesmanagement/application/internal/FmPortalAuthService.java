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

/**
 * Deliberately named FmPortalAuthService, not a generic "PortalAuthService"
 * — handles accepting invites TO THIS MODULE specifically, using the shared
 * PortalUser identity underneath. Direct mirror of
 * TrainProvPortalAuthService/CollAgencyPortalAuthService/WhsePortalAuthService,
 * confirmed identical in shape by direct source read.
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
        log.info("FM portal user registered via invite: {}", user.getEmail());
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
