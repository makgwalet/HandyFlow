package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelPortalAccessGrant;
import za.co.handyflow.platform.fuel.domain.repository.FuelPortalAccessGrantRepository;
import za.co.handyflow.platform.fuel.dto.PortalDtos.AuthResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;

/** Register through an invite and log in. Identity is the shared PortalUser; what they can see is decided only by their grants. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelPortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final FuelPortalAccessGrantRepository grantRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;

    @Transactional
    public AuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        FuelPortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
                .orElseThrow(() -> new HandyFlowException("This invite link is invalid", HttpStatus.BAD_REQUEST, "INVALID_INVITE"));
        if (!grant.isInviteValid()) {
            throw new HandyFlowException("This invite link has expired or has already been used", HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        }
        if (portalUserRepo.findByEmail(grant.getInviteEmail()).isPresent()) {
            throw new HandyFlowException("An account already exists for this email. Please log in instead.", HttpStatus.CONFLICT, "ACCOUNT_EXISTS");
        }
        PortalUser user = PortalUser.create(grant.getInviteEmail(), passwordEncoder.encode(password), fullName);
        portalUserRepo.save(user);
        grant.acceptInvite(user.getId());
        grantRepo.save(grant);
        log.info("[Fuel] Portal user registered via invite: {}", user.getEmail());
        return new AuthResponse(portalJwtService.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        PortalUser user = portalUserRepo.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!user.isActive()) throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        user.recordLogin();
        portalUserRepo.save(user);
        log.info("[Fuel] Portal login: {}", user.getEmail());
        return new AuthResponse(portalJwtService.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }
}
