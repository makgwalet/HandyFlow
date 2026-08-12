package za.co.handyflow.platform.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic, reusable rate-limit counter — same field semantics as
 * contracting.domain.model.OtpRateLimit (requestCount, windowStart), just
 * keyed by a free-form String instead of a UUID partyId, so this one
 * table/entity covers login, registration, and any future public endpoint
 * needing the same protection, rather than a new copy per endpoint.
 * <p>
 * DB-backed rather than in-memory deliberately, for the same reason OTP's
 * rate limiting was migrated off ConcurrentHashMap (see OtpRateLimit's own
 * Javadoc): an in-memory counter is silently per-instance, not per-key,
 * the moment there's more than one app instance behind a load balancer —
 * weakening the limit without any error or warning. Lives in `shared`
 * since it's genuinely cross-cutting infrastructure, not tied to any one
 * business module.
 */
@Entity
@Table(name = "rate_limits")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RateLimitEntry {

    @Id
    @Column(name = "rate_key")
    private String rateKey;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RateLimitEntry startNewWindow(String rateKey, int initialCount) {
        RateLimitEntry e = new RateLimitEntry();
        e.rateKey = rateKey;
        e.requestCount = initialCount;
        e.windowStart = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public boolean isWindowExpired(long windowMs) {
        return Instant.now().toEpochMilli() - windowStart.toEpochMilli() > windowMs;
    }

    public void resetWindow(int initialCount) {
        this.requestCount = initialCount;
        this.windowStart = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void incrementRequestCount() {
        this.requestCount++;
        this.updatedAt = Instant.now();
    }
}