package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpPortalAccessGrant;
import za.co.handyflow.platform.legalpractice.domain.repository.LpPortalAccessGrantRepository;
import za.co.handyflow.platform.legalpractice.dto.LpPortalAuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

/**
 * Direct mirror of the confirmed-real portal auth pattern
 * (Accountant/Booking Agency/Recruitment Agency/Payroll Bureau/Auditor —
 * all read directly from real source this session).
 * {@code shared.PortalUser}/{@code PortalJwtService}/{@code PortalUserRepository}
 * are confirmed real, genuinely shared, portal-type-agnostic.
 * <p>
 * Replaces the earlier bare self-service {@code register()}/{@code login()}
 * (no invite token) that this class previously had — that shape was
 * forced by {@code LpPortalAccessGrant}'s old fixed, required-
 * {@code portalUserId} entity shape, which no longer applies now that the
 * grant carries a real invite-token/email/status state machine.
 * {@code registerViaInvite()} now redeems that token instead of creating
 * an unattached login identity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpPortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final LpPortalAccessGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    @Transactional
    public LpPortalAuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        LpPortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
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
        log.info("Legal practice portal user registered via invite: {}", user.getEmail());
        return new LpPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public LpPortalAuthResponse login(String email, String password) {
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
        log.info("Legal practice portal login: {}", user.getEmail());
        return new LpPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }
}
