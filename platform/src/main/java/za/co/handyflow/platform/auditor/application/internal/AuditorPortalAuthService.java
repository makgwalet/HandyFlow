package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.domain.repository.AuditorAccessGrantRepository;
import za.co.handyflow.platform.auditor.dto.AuditorPortalAuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

/**
 * Direct mirror of the confirmed-real portal auth pattern
 * (Accountant/Booking Agency/Recruitment Agency/Payroll Bureau).
 * shared.PortalUser/PortalJwtService/PortalUserRepository ARE confirmed
 * real, genuinely shared, portal-type-agnostic (read directly from real
 * source earlier this session). FIX from the previous version: the
 * return type is now AuditorPortalAuthResponse (a local DTO in this
 * module's own dto package), not shared.PortalAuthResponse — that class
 * doesn't exist; the compiler confirmed it directly. Every other portal
 * auth service almost certainly defines its own local equivalent the
 * same way, matching the frontend's own per-portal interface convention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditorPortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final AuditorAccessGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    @Transactional
    public AuditorPortalAuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        AuditorAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
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
        log.info("Auditor portal user registered via invite: {}", user.getEmail());
        return new AuditorPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public AuditorPortalAuthResponse login(String email, String password) {
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
        log.info("Auditor portal login: {}", user.getEmail());
        return new AuditorPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }
}