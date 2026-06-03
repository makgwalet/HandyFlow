package za.co.handyflow.platform.contracting.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP service for contract signing.
 *
 * Fixes applied:
 * §1.1 Raw OTP never appears in logs.
 * §1.4 Rate limiting moved to ContractingService (enforced before generateAndStore is called).
 *
 * Remaining production gap:
 * §1.2 Replace in-memory store with Redis — see Redis migration note below.
 *       On multi-instance deployments the OTP generated on instance A cannot be verified
 *       on instance B. This is NOT a "fix later" issue — it silently fails on first
 *       horizontal scale event. Redis migration:
 *         redisTemplate.opsForValue().set("otp:" + partyId, hashed, 10, TimeUnit.MINUTES)
 *
 * Note on storage: we store the BCrypt hash (not plaintext) for verification.
 * The dev endpoint gets the raw OTP from a separate rawStore — in production,
 * disable the dev endpoint entirely (matchIfMissing = false) rather than
 * removing rawStore from the prod store.
 *
 * Note on column size: ContractSignature.otp_code_hash is VARCHAR(64) in the SQL
 * but BCrypt output is 60 chars. This is fine but confusingly named — it is BCrypt,
 * not SHA-256. The migration V55 renames the column and widens it to 100 for safety.
 */
@Slf4j
@Service
public class OtpService {

    private final SecureRandom random = new SecureRandom();

    /** Holds BCrypt hash + expiry. The hash is what we verify against. */
    private final Map<String, HashedEntry> hashStore = new ConcurrentHashMap<>();

    /**
     * Plain-text store — ONLY for the dev OTP endpoint.
     * Never used in verification. In production the dev endpoint is disabled
     * by @ConditionalOnProperty(matchIfMissing = false).
     */
    private final Map<String, RawEntry> rawStore = new ConcurrentHashMap<>();

    private record HashedEntry(String hash, long expiresAt) {}
    private record RawEntry(String otp, long expiresAt) {}

    private static final long OTP_TTL_MS = 10 * 60 * 1_000L;

    /**
     * Generates a cryptographically random 6-digit OTP.
     * Stores the BCrypt hash for verification and the raw value for the dev endpoint.
     * Returns the raw OTP so the caller can send it via SMS.
     *
     * §1.1: Raw OTP is never written to any log.
     */
    public String generateAndStore(String partyId) {
        String otp  = String.format("%06d", random.nextInt(1_000_000));
        String hash = bcrypt(otp);
        long   exp  = System.currentTimeMillis() + OTP_TTL_MS;

        hashStore.put(partyId, new HashedEntry(hash, exp));
        rawStore.put(partyId,  new RawEntry(otp, exp));   // dev only

        log.info("OTP generated for partyId={} — expires in 10 min", partyId);
        return otp;
    }

    /**
     * Verifies the submitted OTP against the stored BCrypt hash.
     * Removes both stores on success (one-time use).
     * Returns false (not exception) on failure so the caller can record the failure.
     */
    public boolean verify(String partyId, String submittedOtp) {
        HashedEntry entry = hashStore.get(partyId);
        if (entry == null) {
            log.warn("OTP verify: no entry for partyId={}", partyId);
            return false;
        }
        if (System.currentTimeMillis() > entry.expiresAt()) {
            hashStore.remove(partyId);
            rawStore.remove(partyId);
            log.warn("OTP verify: expired for partyId={}", partyId);
            return false;
        }
        boolean valid = org.springframework.security.crypto.bcrypt.BCrypt
                .checkpw(submittedOtp, entry.hash());
        if (valid) {
            hashStore.remove(partyId);
            rawStore.remove(partyId);
            log.info("OTP verified for partyId={}", partyId);
        }
        return valid;
    }

    /**
     * Returns the raw OTP string — ONLY for the dev controller.
     * Returns null if no active OTP exists or it has expired.
     */
    public String getStoredOtp(String partyId) {
        RawEntry entry = rawStore.get(partyId);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiresAt()) {
            rawStore.remove(partyId);
            hashStore.remove(partyId);
            return null;
        }
        return entry.otp();
    }

    /**
     * Returns a fresh BCrypt hash of the OTP for storing in the audit signature record.
     * Called by ContractingService.recordSignature() after a successful verify().
     */
    public String hashOtp(String otp) {
        return bcrypt(otp);
    }

    private String bcrypt(String value) {
        return org.springframework.security.crypto.bcrypt.BCrypt
                .hashpw(value, org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10));
    }
}
