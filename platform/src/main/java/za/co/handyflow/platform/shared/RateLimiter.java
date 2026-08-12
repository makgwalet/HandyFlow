package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic rate limiter — one mechanism for any endpoint that needs one,
 * rather than a bespoke implementation per controller. Same
 * atomic-read-modify-write pattern ContractingService.checkOtpRateLimit()
 * already established, generalized to any String key instead of just OTP
 * requests by partyId.
 * <p>
 * USAGE: call tryConsume() at the top of a controller method or in a
 * filter, before doing any real work. Returns false when the caller
 * should be rejected (429), true when the request may proceed.
 * <p>
 * Example — login rate limiting by IP:
 *   if (!rateLimiter.tryConsume("auth:login:" + clientIp, 10, 60_000)) {
 *       throw new TooManyRequestsException("Too many login attempts — try again shortly");
 *   }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private final RateLimitEntryRepository repo;

    /**
     * @param key       caller-supplied identifier, e.g. "auth:login:203.0.113.5".
     *                  Always prefix with a scope so different endpoints'
     *                  counters can't collide for the same underlying IP/user.
     * @param maxCount  maximum requests allowed within windowMs
     * @param windowMs  sliding window length in milliseconds
     * @return          true if this request is allowed (and has been counted),
     *                  false if the limit was already reached for this window
     */
    @Transactional
    public boolean tryConsume(String key, int maxCount, long windowMs) {
        RateLimitEntry entry = repo.findByKeyForUpdate(key).orElse(null);

        if (entry == null) {
            repo.save(RateLimitEntry.startNewWindow(key, 1));
            return true;
        }

        if (entry.isWindowExpired(windowMs)) {
            entry.resetWindow(1);
            repo.save(entry);
            return true;
        }

        if (entry.getRequestCount() >= maxCount) {
            log.warn("Rate limit exceeded for key={} (count={}, max={})",
                    key, entry.getRequestCount(), maxCount);
            return false;
        }

        entry.incrementRequestCount();
        repo.save(entry);
        return true;
    }
}