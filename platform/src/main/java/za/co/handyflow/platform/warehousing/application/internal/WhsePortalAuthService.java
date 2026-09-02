package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.warehousing.domain.model.WhsePortalAccessGrant;
import za.co.handyflow.platform.warehousing.domain.repository.WhsePortalAccessGrantRepository;
import za.co.handyflow.platform.warehousing.dto.PortalAuthResponse;

/**
 * Direct mirror of CollAgencyPortalAuthService — shared.PortalUser/
 * PortalJwtService/PortalUserRepository are genuinely shared, portal-
 * type-agnostic identity infrastructure; what a logged-in user can
 * actually see is entirely governed by WhsePortalAccessGrant, never by
 * this class. Deliberately has no acceptAdditionalInvite() in this first
 * pass, same call made for Collections Agency/Payroll Bureau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhsePortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final WhsePortalAccessGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    @Transactional
    public PortalAuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        WhsePortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
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
        log.info("[Warehousing] Portal user registered via invite: {}", user.getEmail());
        return new PortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public PortalAuthResponse login(String email, String password) {
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
        log.info("[Warehousing] Portal login: {}", user.getEmail());
        return new PortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }
}
