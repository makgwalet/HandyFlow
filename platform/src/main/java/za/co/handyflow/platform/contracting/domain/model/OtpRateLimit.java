package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Database-backed replacement for {@code ContractingService.otpRateStore}
 * ({@code ConcurrentHashMap<String, RateEntry>}). Same multi-instance
 * problem as {@link OtpVerification} — a party's request/failure counts on
 * instance A were invisible to instance B, meaning the rate limit was
 * effectively per-instance rather than per-party the moment there was more
 * than one instance behind a load balancer (silently weakening the limit,
 * not just losing data).
 * <p>
 * Field names and semantics deliberately mirror the original in-memory
 * RateEntry record exactly (requestCount, failCount, windowStart) — see
 * ContractingService.checkOtpRateLimit()/recordOtpFailure()/
 * clearOtpFailures() for the unchanged business logic now reading and
 * writing this entity instead of a ConcurrentHashMap entry.
 */
@Entity
@Table(name = "contract_otp_rate_limits")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtpRateLimit {

    @Id
    @Column(name = "party_id")
    private UUID partyId;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OtpRateLimit startNewWindow(UUID partyId, int initialRequestCount, int initialFailCount) {
        OtpRateLimit r = new OtpRateLimit();
        r.partyId = partyId;
        r.requestCount = initialRequestCount;
        r.failCount = initialFailCount;
        r.windowStart = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public boolean isWindowExpired(long windowMs) {
        return Instant.now().toEpochMilli() - windowStart.toEpochMilli() > windowMs;
    }

    public void incrementRequestCount() {
        this.requestCount++;
        this.updatedAt = Instant.now();
    }

    public void resetWindow(int initialRequestCount, int initialFailCount) {
        this.requestCount = initialRequestCount;
        this.failCount = initialFailCount;
        this.windowStart = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void incrementFailCount() {
        this.failCount++;
        this.updatedAt = Instant.now();
    }

    /**
     * Effectively locks out further OTP requests for a fresh 10-minute
     * window starting now. Matches the original ConcurrentHashMap version's
     * behavior exactly: hitting the failure threshold didn't just cap the
     * request count on whatever time was left in the current window — it
     * started a brand new RateEntry with windowStart = now, extending the
     * lockout to a full fresh window rather than letting it expire whenever
     * the original window happened to end.
     */
    public void lockOutRequests(int maxRequests) {
        this.requestCount = maxRequests;
        this.windowStart = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void clearFailures() {
        this.failCount = 0;
        this.updatedAt = Instant.now();
    }
}
