package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Database-backed replacement for {@code OtpService.hashStore}
 * ({@code ConcurrentHashMap<String, HashedEntry>}). The original in-memory
 * store's own class Javadoc called this out directly: "On multi-instance
 * deployments the OTP generated on instance A cannot be verified on
 * instance B. This is NOT a 'fix later' issue — it silently fails on first
 * horizontal scale event." This entity is that fix.
 * <p>
 * One row per party — a new OTP request overwrites the previous row rather
 * than accumulating history, matching the original store's behavior exactly
 * (each {@code generateAndStore} call replaced whatever was in the map for
 * that partyId).
 * <p>
 * Deliberately holds only the BCrypt hash, never the raw OTP — same
 * separation the original code already had between hashStore (used for
 * verification) and rawStore (dev-endpoint only, stays in-memory — see
 * OtpService's Javadoc for why that one wasn't worth migrating).
 */
@Entity
@Table(name = "contract_otp_verifications")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OtpVerification {

    @Id
    @Column(name = "party_id")
    private UUID partyId;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static OtpVerification create(UUID partyId, String otpHash, Instant expiresAt) {
        OtpVerification v = new OtpVerification();
        v.partyId = partyId;
        v.otpHash = otpHash;
        v.expiresAt = expiresAt;
        v.createdAt = Instant.now();
        return v;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
