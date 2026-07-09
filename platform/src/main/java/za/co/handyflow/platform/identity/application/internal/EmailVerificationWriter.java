package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Kept as a separate class from EmailVerificationService for the same
 * reason RefreshTokenSecuritySweep is separate from RefreshTokenService —
 * Spring's transaction proxying doesn't apply to a method calling another
 * @Transactional method on `this`, so REQUIRES_NEW genuinely needs its
 * own bean to have any effect at all.
 * <p>
 * WHY REQUIRES_NEW and saveAndFlush() specifically, and why this exists:
 * <p>
 * Confirmed via real testing that a simple isEmailVerified() check before
 * writing (see EmailVerificationService's history) wasn't enough — two
 * requests can both read emailVerified=false before either commits, a
 * true simultaneous race that no pre-write check can prevent. Only
 * handling the write's own failure can.
 * <p>
 * Two further problems with just wrapping the write in a try/catch inside
 * the caller's own transaction, worked through before shipping this:
 * Spring Data JPA's plain save() doesn't necessarily flush immediately —
 * the version check that actually detects the conflict often only
 * happens at transaction commit time, which is after the calling method
 * has already returned, meaning a try/catch around save() alone might
 * never actually catch anything. saveAndFlush() forces that check to
 * happen synchronously, inside this method, where it can genuinely be
 * caught. And even once caught, an optimistic-lock failure during flush
 * can leave the persistence context unusable for anything else run
 * afterward in the same transaction — REQUIRES_NEW means a lost race
 * here is fully isolated to this one mini-transaction; the caller's own
 * transaction (which still needs to mark the token used) is never
 * touched by it either way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EmailVerificationWriter {

    private final UserRepository userRepository;

    /**
     * @return true if this call actually performed the verification,
     *         false if the user was already verified — either before
     *         this call started, or because it lost a race to a
     *         concurrent request (most likely an email security scanner
     *         pre-fetching the link before a human's real click).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryVerify(UUID userId, UUID tenantId) {
        User user = userRepository.findByIdAndTenantId(userId, TenantId.of(tenantId))
                .orElseThrow(() -> new HandyFlowException(
                        "User not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        if (user.isEmailVerified()) {
            return false;
        }

        try {
            user.verifyEmail();
            userRepository.saveAndFlush(user);
            return true;
        } catch (ObjectOptimisticLockingFailureException e) {
            // The only thing that can touch User.emailVerified at all is
            // this same method — losing this race IS the confirmation
            // that a concurrent call already succeeded, not a real
            // failure to report.
            log.info("Lost optimistic-lock race verifying userId={} — already verified by a concurrent request", userId);
            return false;
        }
    }
}