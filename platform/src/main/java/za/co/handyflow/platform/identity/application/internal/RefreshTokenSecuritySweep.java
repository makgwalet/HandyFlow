package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.repository.RefreshTokenRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Kept as a separate class from RefreshTokenService specifically because
 * Spring's declarative transaction management works via proxies — a
 * method calling another @Transactional method on `this` bypasses the
 * proxy entirely and the propagation setting has no effect. This needs
 * to be a genuinely separate bean to actually work, matching exactly the
 * same reasoning TenantAdminRecipientsImpl already documents for the
 * identical mechanism.
 * <p>
 * WHY REQUIRES_NEW specifically, and why this exists at all:
 * <p>
 * Confirmed via real testing, not theoretical: RefreshTokenService.
 * refresh()'s reuse-detection branch called the repository's bulk revoke
 * directly, then threw an exception on the very next line, inside the
 * same @Transactional method. Spring's default rollback-on-exception
 * behaviour rolled back that revocation right along with everything
 * else — the 401 response looked completely correct to the caller, but
 * the "kill every active session for this user" security response
 * silently never persisted at all. A stolen-but-not-yet-rotated token
 * would have kept working even after the legitimate user's own refresh
 * attempt tripped the reuse alarm. REQUIRES_NEW forces this onto its own
 * independent transaction that commits the moment it completes,
 * regardless of what the calling transaction does afterward — including
 * rolling back to produce the 401 the caller still needs to return.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenSecuritySweep {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllActiveSessionsForUser(UUID userId) {
        return refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }
}