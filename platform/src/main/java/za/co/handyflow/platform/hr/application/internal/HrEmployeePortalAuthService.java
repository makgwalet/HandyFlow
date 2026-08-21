package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeePortalAccessGrant;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeePortalAccessGrantRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.HrPortalAuthResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalJwtService;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * FIX: backlog 3.4. registerViaInvite()/login() are a direct structural
 * mirror of the confirmed-real
 * accountant.AccountantPortalAuthService / auditor.AuditorPortalAuthService
 * pattern — same shared.PortalUser/PortalJwtService/PortalUserRepository
 * usage, same invite-token validation shape, same "reject rather than
 * silently create a second identity" handling for an email that already
 * has a PortalUser. Deliberately a local HrPortalAuthResponse DTO (not a
 * shared one), same reasoning as every other portal auth service's own
 * local response type.
 * <p>
 * createInvite() is the half every other portal auth service also has in
 * some form (an admin/staff action that creates the PENDING grant an
 * employee later accepts) — here it's the "HR invites an employee to
 * self-service" action, gated separately at the controller
 * (HR_MANAGE), not something a portal user can call on themselves.
 * <p>
 * ASSUMPTION FLAGGED, NOT GUESSED PAST: the invite link's frontend URL is
 * built from a new `handyflow.frontend-url` property with a placeholder
 * default. Every other portal invite flow in this codebase almost
 * certainly already has a real, confirmed frontend base URL property in
 * use — I did not have visibility into it this session, so rather than
 * silently reuse a guessed property name that might not exist, this
 * introduces its own clearly-named property. Worth consolidating onto
 * whichever property the other four portals actually use, once
 * confirmed, rather than leaving a second parallel one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrEmployeePortalAuthService {

    private final PortalUserRepository portalUserRepo;
    private final HrEmployeePortalAccessGrantRepository grantRepo;
    private final HrEmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService portalJwtService;
    private final EmailService emailService;

    @Value("${handyflow.frontend-url:https://app.handyflow.co.za}")
    private String frontendUrl;

    // ── Admin side: HR invites an employee ──────────────────────────────────

    @Transactional
    public void createInvite(TenantId tenantId, UUID employeeId, String inviteEmailOverride, UUID invitedBy) {
        HrEmployee emp = employeeRepo.findActiveById(tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId.toString()));

        String inviteEmail = inviteEmailOverride != null && !inviteEmailOverride.isBlank()
                ? inviteEmailOverride
                : emp.getEmail();

        if (inviteEmail == null || inviteEmail.isBlank()) {
            throw new HandyFlowException(
                    "This employee has no email on file — provide an inviteEmail to send the portal invite to",
                    HttpStatus.BAD_REQUEST, "NO_EMAIL");
        }

        HrEmployeePortalAccessGrant grant = HrEmployeePortalAccessGrant.createInvite(
                tenantId.getValue(), employeeId, inviteEmail, invitedBy);
        grantRepo.save(grant);

        String link = frontendUrl + "/hr-portal/register?token=" + grant.getInviteToken();
        try {
            emailService.send(inviteEmail, "You're invited to the employee self-service portal",
                    "Hi " + emp.getFirstName() + ",\n\n"
                            + "You've been invited to set up self-service access — view your payslips, "
                            + "check your leave balance, and request leave online.\n\n"
                            + "Set up your account: " + link + "\n\n"
                            + "This link expires in 7 days.");
        } catch (Exception e) {
            // Same principle as every other notification hookup in this
            // codebase: the grant is already saved — an email failure here
            // must not undo it. HR can re-check via the grant list and
            // resend if needed.
            log.warn("[HrPortal] Invite email failed for employee={} tenant={}: {}",
                    employeeId, tenantId, e.getMessage());
        }

        log.info("[HrPortal] Portal invite created for employee={} tenant={}", employeeId, tenantId);
    }

    // ── Portal user side: register / login ───────────────────────────────────

    @Transactional
    public HrPortalAuthResponse registerViaInvite(String inviteToken, String password, String fullName) {
        HrEmployeePortalAccessGrant grant = grantRepo.findByInviteToken(inviteToken)
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
        log.info("[HrPortal] Employee portal user registered via invite: {}", user.getEmail());
        return new HrPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public HrPortalAuthResponse login(String email, String password) {
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
        log.info("[HrPortal] Employee portal login: {}", user.getEmail());
        return new HrPortalAuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }
}