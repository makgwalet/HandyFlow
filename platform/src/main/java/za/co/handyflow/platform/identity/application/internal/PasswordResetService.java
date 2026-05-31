package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.PasswordResetToken;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.PasswordResetTokenRepository;
import za.co.handyflow.platform.identity.domain.repository.TenantRepository;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.identity.dto.request.ForgotPasswordRequest;
import za.co.handyflow.platform.identity.dto.request.ResetPasswordRequest;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final TenantRepository            tenantRepository;
    private final UserRepository              userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final EmailService                emailService;

    // ── Step 1: Request reset ─────────────────────────────────────────────────

    @Transactional
    public void requestReset(ForgotPasswordRequest req) {
        // WHY always return success even if email not found?
        // Prevents user enumeration attacks — attacker can't tell if an
        // email is registered by observing different responses.
        var tenant = tenantRepository.findBySlug(req.tenantSlug()).orElse(null);
        if (tenant == null) {
            log.warn("Forgot password: unknown slug={}", req.tenantSlug());
            return; // silently return — don't reveal slug doesn't exist
        }

        TenantId tenantId = tenant.getTenantId();
        User user = userRepository
                .findByEmailAndTenantId(req.email().toLowerCase().trim(), tenantId)
                .orElse(null);

        if (user == null) {
            log.warn("Forgot password: unknown email={} in tenant={}", req.email(), tenant.getSlug());
            return; // silently return — don't reveal email doesn't exist
        }

        if (!user.isActive()) {
            log.warn("Forgot password: inactive user={}", user.getId());
            return; // don't allow reset for deactivated accounts
        }

        // Invalidate any existing unused tokens for this user
        resetTokenRepository.invalidateAllForUser(user.getId());

        // Create new token
        PasswordResetToken prt = PasswordResetToken.create(user.getId(), tenant.getId());
        resetTokenRepository.save(prt);

        // Send reset email
        String link    = "https://app.handyflow.co.za/reset-password?token=" + prt.getToken();
        String subject = "Reset your HandyFlow password";
        String html    = """
                <p>Hi %s,</p>
                <p>We received a request to reset your HandyFlow password.</p>
                <p>
                  <a href="%s" style="background:#1B3A6B;color:white;padding:12px 24px;
                     border-radius:8px;text-decoration:none;font-weight:bold;display:inline-block">
                     Reset password
                  </a>
                </p>
                <p>This link expires in <strong>1 hour</strong>.</p>
                <p>If you didn't request this, you can safely ignore this email.
                   Your password has not been changed.</p>
                """.formatted(user.getFirstName(), link);

        try {
            emailService.send(req.email(), subject, html);
            log.info("Password reset email sent to={} tenant={}", req.email(), tenant.getSlug());
        } catch (Exception e) {
            log.error("Failed to send reset email to={}: {}", req.email(), e.getMessage());
        }
    }

    // ── Step 2: Reset password ────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken prt = resetTokenRepository.findByToken(req.token())
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid or expired reset link",
                        HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN"));

        if (!prt.isValid()) {
            throw new HandyFlowException(
                    prt.isExpired()
                            ? "This reset link has expired. Please request a new one."
                            : "This reset link has already been used.",
                    HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN");
        }

        TenantId tenantId = TenantId.of(prt.getTenantId());
        User user = userRepository.findByIdAndTenantId(prt.getUserId(), tenantId)
                .orElseThrow(() -> new HandyFlowException(
                        "User not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (!user.isActive()) {
            throw new HandyFlowException(
                    "This account has been deactivated",
                    HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        prt.markUsed();
        resetTokenRepository.save(prt);

        log.info("Password reset successful for userId={}", user.getId());
    }
}
