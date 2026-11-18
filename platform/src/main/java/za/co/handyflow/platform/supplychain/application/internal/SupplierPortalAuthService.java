package za.co.handyflow.platform.supplychain.application.internal;

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
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierPortalGrant;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierPortalGrantRepository;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.AuthResponse;

/**
 * Registration by invite and login for supplier portal users. The identity (shared PortalUser) says only who the person is;
 * what they may see is decided by their active ScSupplierPortalGrant rows, in SupplierPortalService.
 * A person who already has a portal account (for another company or module) accepts a new invite by logging in and
 * presenting the token, so they keep one login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierPortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final ScSupplierPortalGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    @Transactional
    public AuthResponse register(String inviteToken, String password, String fullName) {
        if (password == null || password.length() < 10)
            throw new HandyFlowException("Choose a password of at least 10 characters", HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
        if (fullName == null || fullName.isBlank()) throw new HandyFlowException("Your name is required", HttpStatus.BAD_REQUEST, "NAME_REQUIRED");
        ScSupplierPortalGrant grant = grantFor(inviteToken);
        if (portalUserRepo.findByEmail(grant.getInviteEmail()).isPresent())
            throw new HandyFlowException("An account already exists for this email: log in and accept the invite instead",
                    HttpStatus.CONFLICT, "ACCOUNT_EXISTS");
        PortalUser user = portalUserRepo.save(PortalUser.create(grant.getInviteEmail(), passwordEncoder.encode(password), fullName.trim()));
        grant.acceptInvite(user.getId());
        grantRepo.save(grant);
        log.info("[SCM] Supplier portal user registered: {}", user.getEmail());
        return new AuthResponse(portalJwtService.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    /** Log in, optionally accepting a new invite addressed to the same email in the same step. */
    @Transactional
    public AuthResponse login(String email, String password, String inviteToken) {
        PortalUser user = portalUserRepo.findByEmail(email == null ? "" : email.toLowerCase().trim())
                .orElseThrow(() -> new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!user.isActive()) throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash()))
            throw new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        if (inviteToken != null && !inviteToken.isBlank()) {
            ScSupplierPortalGrant grant = grantFor(inviteToken);
            if (!grant.getInviteEmail().equalsIgnoreCase(user.getEmail()))
                throw new HandyFlowException("This invite was sent to a different email address", HttpStatus.FORBIDDEN, "INVITE_EMAIL_MISMATCH");
            grant.acceptInvite(user.getId());
            grantRepo.save(grant);
        }
        user.recordLogin();
        portalUserRepo.save(user);
        return new AuthResponse(portalJwtService.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    private ScSupplierPortalGrant grantFor(String inviteToken) {
        ScSupplierPortalGrant grant = inviteToken == null ? null : grantRepo.findByInviteToken(inviteToken).orElse(null);
        if (grant == null) throw new HandyFlowException("This invite link is invalid", HttpStatus.BAD_REQUEST, "INVALID_INVITE");
        if (!grant.isInviteValid())
            throw new HandyFlowException("This invite link has expired or has already been used", HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        return grant;
    }
}
