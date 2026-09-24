package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.SupportAction;
import za.co.handyflow.platform.shared.SupportActionResult;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The reference implementation of the support-action framework — see
 * {@code shared.SupportAction}'s own Javadoc for the full architecture.
 * This class lives in {@code identity}, exactly where the logic it needs
 * (issuing a new verification token, looking up the user) already lives;
 * it is a plain {@code @Component} implementing a {@code shared}
 * interface, nothing more.
 * <p>
 * {@code targetId} is the user's id — this action always acts on one
 * specific person's unverified account, never the whole tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResendVerificationEmailAction implements SupportAction {

    private static final String VERIFY_LINK_BASE = "https://app.handyflow.co.za/verify-email?token=";

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;

    @Override
    public String key() { return "RESEND_VERIFICATION_EMAIL"; }

    @Override
    public String label() { return "Resend verification email"; }

    @Override
    public String description() {
        return "Issues a fresh verification link and emails it to the user. Use when someone's original "
                + "link expired or the email never arrived. No-op if the user is already verified.";
    }

    @Override
    public SupportActionResult execute(TenantId tenantId, UUID targetId, Map<String, String> params, UUID performedByAdminId) {
        if (targetId == null) {
            return SupportActionResult.failure("targetId (the user's id) is required for this action");
        }

        Optional<User> userOpt = userRepository.findByIdAndTenantId(targetId, tenantId);
        if (userOpt.isEmpty()) {
            return SupportActionResult.failure("No user with id " + targetId + " found for this tenant");
        }
        User user = userOpt.get();

        if (user.isEmailVerified()) {
            return SupportActionResult.failure(user.getEmail() + " is already verified — nothing to resend");
        }

        String token = emailVerificationService.createToken(user.getId(), tenantId.getValue());
        String verifyLink = VERIFY_LINK_BASE + token;

        emailService.send(user.getEmail(), "Verify your email address",
                EmailTemplates.resendVerificationEmail(user.getFirstName(), verifyLink));

        log.info("Verification email resent userId={} tenant={} triggeredByAdmin={}", user.getId(), tenantId, performedByAdminId);
        return SupportActionResult.success("Verification email resent to " + user.getEmail());
    }
}
