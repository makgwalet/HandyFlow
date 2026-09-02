package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyPortalAccessGrant;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyPortalAccessGrantRepository;
import za.co.handyflow.platform.collectionsagency.dto.PortalAuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

/**
 * Direct mirror of the confirmed-real portal auth pattern already proven
 * four times over (Accountant/Booking Agency/Recruitment Agency/Payroll
 * Bureau, and again on Auditor). shared.PortalUser/PortalJwtService/
 * PortalUserRepository are confirmed real, genuinely shared, portal-
 * type-agnostic identity infrastructure — a person who is both a
 * creditor client of this agency and, say, a recruitment-agency client
 * elsewhere can use one login. What that login can actually see is
 * entirely governed by CollAgencyPortalAccessGrant, never by this class.
 * <p>
 * Deliberately does NOT include acceptAdditionalInvite() (the
 * accountant/auditor-only extra: letting an already-logged-in portal
 * user accept a second invite without re-registering) — left out of
 * this first pass rather than half-implemented, same call made for the
 * Payroll Bureau equivalent; add it the same way if/when needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollAgencyPortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final CollAgencyPortalAccessGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    @Transactional
    public PortalAuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        CollAgencyPortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
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
        log.info("[CollectionsAgency] Portal user registered via invite: {}", user.getEmail());
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
        log.info("[CollectionsAgency] Portal login: {}", user.getEmail());
        return new PortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }
}
