package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.OtpVerification;
import za.co.handyflow.platform.contracting.domain.repository.OtpVerificationRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP service for contract signing.
 *
 * Fixes applied:
 * §1.1 Raw OTP never appears in logs.
 * §1.4 Rate limiting moved to ContractingService (enforced before generateAndStore is called).
 * §1.2 FIX APPLIED: hashStore is now backed by OtpVerification (a real table), not a
 *       ConcurrentHashMap. This was the actual production blocker called out in the
 *       Javadoc below — "silently fails on first horizontal scale event" — now resolved.
 *       See OtpVerification's Javadoc for the full rationale, and the decision to use a
 *       database table over Redis (no new infrastructure needed for this volume of traffic).
 *
 * Note on storage: we store the BCrypt hash (not plaintext) for verification.
 * The dev endpoint gets the raw OTP from a separate rawStore — in production,
 * disable the dev endpoint entirely (matchIfMissing = false) rather than
 * removing rawStore from the prod store.
 *
 * rawStore DELIBERATELY remains in-memory (not migrated): it's only ever read by
 * OtpDevController, which is disabled in production via
 * @ConditionalOnProperty(matchIfMissing = false). Dev/local environments are
 * effectively always single-instance, so the multi-instance problem this class
 * exists to fix doesn't apply to rawStore the way it did to hashStore.
 *
 * Note on column size: ContractSignature.otp_code_hash is VARCHAR(64) in the SQL
 * but BCrypt output is 60 chars. This is fine but confusingly named — it is BCrypt,
 * not SHA-256. The migration V55 renames the column and widens it to 100 for safety.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpVerificationRepository otpVerificationRepo;

    private final SecureRandom random = new SecureRandom();

    /**
     * Plain-text store — ONLY for the dev OTP endpoint.
     * Never used in verification. In production the dev endpoint is disabled
     * by @ConditionalOnProperty(matchIfMissing = false). Deliberately still
     * in-memory — see class Javadoc for why this one wasn't migrated.
     */
    private final Map<String, RawEntry> rawStore = new ConcurrentHashMap<>();

    private record RawEntry(String otp, long expiresAt) {}

    private static final long OTP_TTL_MS = 10 * 60 * 1_000L;

    /**
     * Generates a cryptographically random 6-digit OTP.
     * Stores the BCrypt hash for verification and the raw value for the dev endpoint.
     * Returns the raw OTP so the caller can send it via SMS.
     *
     * §1.1: Raw OTP is never written to any log.
     */
    @Transactional
    public String generateAndStore(String partyId) {
        String otp  = String.format("%06d", random.nextInt(1_000_000));
        String hash = bcrypt(otp);
        long   exp  = System.currentTimeMillis() + OTP_TTL_MS;

        UUID partyUuid = UUID.fromString(partyId);
        // FIX: was hashStore.put(partyId, new HashedEntry(hash, exp)) on a
        // ConcurrentHashMap. save() with the same @Id (partyId) overwrites
        // any existing row, matching the original "one active OTP per
        // party, newest replaces oldest" behavior exactly.
        otpVerificationRepo.save(OtpVerification.create(
                partyUuid, hash, Instant.ofEpochMilli(exp)));

        rawStore.put(partyId, new RawEntry(otp, exp));   // dev only

        log.info("OTP generated for partyId={} — expires in 10 min", partyId);
        return otp;
    }

    /**
     * Verifies the submitted OTP against the stored BCrypt hash.
     * Removes both stores on success (one-time use).
     * Returns false (not exception) on failure so the caller can record the failure.
     */
    @Transactional
    public boolean verify(String partyId, String submittedOtp) {
        UUID partyUuid = UUID.fromString(partyId);
        Optional<OtpVerification> entryOpt = otpVerificationRepo.findById(partyUuid);
        if (entryOpt.isEmpty()) {
            log.warn("OTP verify: no entry for partyId={}", partyId);
            return false;
        }
        OtpVerification entry = entryOpt.get();
        if (entry.isExpired()) {
            deleteIfExists(partyUuid);
            rawStore.remove(partyId);
            log.warn("OTP verify: expired for partyId={}", partyId);
            return false;
        }
        boolean valid = org.springframework.security.crypto.bcrypt.BCrypt
                .checkpw(submittedOtp, entry.getOtpHash());
        if (valid) {
            deleteIfExists(partyUuid);
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
            deleteIfExists(UUID.fromString(partyId));
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

    // FIX: JpaRepository.deleteById(...) throws EmptyResultDataAccessException
    // if the row is already gone — unlike ConcurrentHashMap.remove(...),
    // which was always a safe no-op on a missing key. Both call sites above
    // can legitimately race against the row already being deleted elsewhere
    // (verify() removing it after a successful check, or a prior expiry
    // cleanup), so deletion here must be tolerant of "already gone" rather
    // than treating it as an error.
    private void deleteIfExists(UUID partyId) {
        otpVerificationRepo.findById(partyId).ifPresent(otpVerificationRepo::delete);
    }
}
