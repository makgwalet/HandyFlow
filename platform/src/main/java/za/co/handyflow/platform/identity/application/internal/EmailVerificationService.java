package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.EmailVerificationToken;
import za.co.handyflow.platform.identity.domain.repository.EmailVerificationTokenRepository;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.UUID;

/**
 * NEW feature. Deliberately non-blocking throughout — verifyEmail() only
 * ever marks a user as verified, it never gates login or anything else.
 * See V_email_verification.sql for the full reasoning.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationWriter           writer;

    // Split from verifyEmail() rather than a single "register and email"
    // method, since AuthService needs the raw token to build the
    // verification link BEFORE building the welcome email that carries
    // it — the token has to exist first.
    @Transactional
    public String createToken(UUID userId, UUID tenantId) {
        EmailVerificationToken evt = EmailVerificationToken.create(userId, tenantId);
        tokenRepository.save(evt);
        return evt.getToken();
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken evt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Invalid verification link",
                        HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_TOKEN"));

        if (evt.isExpired()) {
            throw new HandyFlowException(
                    "This verification link has expired. Please request a new one.",
                    HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_EXPIRED");
        }

        // FIX: previously did the User lookup and write inline here.
        // Confirmed via real testing (email_verified was false at the
        // exact moment of the error, true immediately after) that a true
        // simultaneous race can occur — two requests both reading
        // emailVerified=false before either commits, most likely an
        // email security scanner pre-fetching the verification link
        // before a human's real click. Delegated to
        // EmailVerificationWriter specifically because handling that
        // race correctly needs its own isolated transaction and a forced
        // flush — see that class's own comment for the full reasoning,
        // including two subtler problems (deferred flush timing, a
        // poisoned persistence context) that made a simple try/catch
        // right here insufficient.
        boolean verifiedByThisCall = writer.tryVerify(evt.getUserId(), evt.getTenantId());

        if (verifiedByThisCall) {
            evt.markUsed();
            tokenRepository.save(evt);
            log.info("Email verified for userId={}", evt.getUserId());
        } else {
            log.info("Verification token={} presented but user already verified — no-op", evt.getId());
        }
    }
}