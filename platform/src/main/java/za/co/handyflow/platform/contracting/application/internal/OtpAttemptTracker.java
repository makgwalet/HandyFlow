package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.repository.OtpVerificationRepository;

import java.util.UUID;

/**
 * Counts wrong OTP guesses and destroys the OTP after {@value #MAX_ATTEMPTS}.
 * <p>
 * Runs in its own transaction on purpose: the signing request that made the wrong guess throws and rolls back, and the
 * count must survive that, or the limit would never be reached.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OtpAttemptTracker {

    static final int MAX_ATTEMPTS = 5;

    private final OtpVerificationRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID partyId) {
        if (repo.incrementFailedAttempts(partyId) == 0) return;
        repo.findById(partyId).filter(v -> v.getFailedAttempts() >= MAX_ATTEMPTS).ifPresent(v -> {
            repo.delete(v);
            log.warn("OTP for partyId={} destroyed after {} wrong attempts. A new code must be requested.", partyId, MAX_ATTEMPTS);
        });
    }
}
