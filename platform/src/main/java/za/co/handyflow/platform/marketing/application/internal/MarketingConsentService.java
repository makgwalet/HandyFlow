package za.co.handyflow.platform.marketing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.marketing.domain.model.MktConsentEvidence;
import za.co.handyflow.platform.marketing.domain.model.MktContactPreference;
import za.co.handyflow.platform.marketing.domain.model.MktSuppressionEntry;
import za.co.handyflow.platform.marketing.domain.repository.MktConsentEvidenceRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktSuppressionEntryRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * POPIA / Section 69 consent and suppression: the single place that decides whether an address may be mailed.
 *
 * - {@link #evaluate} is the ONE eligibility decision, used at launch, again before every send, and by journeys.
 * - Suppression is per tenant + address; the unique active-row index makes concurrent suppressions harmless.
 * - Lifting a suppression never re-opts the contact in: they must opt in again, with fresh evidence.
 * - {@code mkt_consent_evidence} is append-only: every opt-in, opt-out, import declaration and
 *   suppression change adds a row, and rows are never updated or deleted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingConsentService {

    /** Human-readable reasons carried on blocked sends. */
    public static final String BLOCKED_SUPPRESSED   = "address is on the suppression list";
    public static final String BLOCKED_NOT_OPTED_IN = "contact has not opted in to email marketing";
    public static final String BLOCKED_NO_CONTACT   = "contact record not found";
    public static final String BLOCKED_BAD_ADDRESS  = "invalid email address";

    private static final int MAX_EVIDENCE_CHARS = 2000;
    private static final Set<String> SUPPRESSION_REASONS = Set.of(
            MktSuppressionEntry.REASON_UNSUBSCRIBED, MktSuppressionEntry.REASON_COMPLAINT,
            MktSuppressionEntry.REASON_HARD_BOUNCE,  MktSuppressionEntry.REASON_MANUAL);

    private final MktSuppressionEntryRepository suppressionRepo;
    private final MktConsentEvidenceRepository  evidenceRepo;

    /** Outcome of an eligibility check. {@code reason} is null when eligible. */
    public record Decision(boolean eligible, String reason) {
        public static Decision ok()                  { return new Decision(true, null); }
        public static Decision blocked(String why)   { return new Decision(false, why); }
    }

    // ── Eligibility ───────────────────────────────────────────────────────────

    /**
     * Pure decision (no database access): may {@code email} be mailed given this contact record and the
     * set of actively suppressed addresses? Fails closed: a missing contact, a missing opt-in or a
     * suppressed address is never eligible.
     *
     * @param suppressed lower-case suppressed addresses, as returned by {@link #activeSuppressedEmails}
     */
    public Decision evaluate(MktContactPreference pref, String email, Set<String> suppressed) {
        String e = normalise(email);
        if (pref == null)                                    return Decision.blocked(BLOCKED_NO_CONTACT);
        if (e == null || !e.contains("@"))                   return Decision.blocked(BLOCKED_BAD_ADDRESS);
        if (suppressed != null && suppressed.contains(e))    return Decision.blocked(BLOCKED_SUPPRESSED);
        if (!pref.isEmailOptedIn())                          return Decision.blocked(BLOCKED_NOT_OPTED_IN);
        return Decision.ok();
    }

    @Transactional(readOnly = true)
    public boolean isSuppressed(TenantId tenantId, String email) {
        String e = normalise(email);
        return e != null && suppressionRepo.findActive(tenantId, e).isPresent();
    }

    @Transactional(readOnly = true)
    public Set<String> activeSuppressedEmails(TenantId tenantId) {
        return new HashSet<>(suppressionRepo.findAllActiveEmails(tenantId));
    }

    /** The subset of {@code emails} that is actively suppressed (batched, so large sends stay cheap). */
    @Transactional(readOnly = true)
    public Set<String> activeSuppressedAmong(TenantId tenantId, Collection<String> emails) {
        Set<String> out = new HashSet<>();
        if (emails == null || emails.isEmpty()) return out;
        List<String> lower = emails.stream().map(MarketingConsentService::normalise)
                .filter(x -> x != null).distinct().toList();
        for (int i = 0; i < lower.size(); i += 1000) {     // keep each IN list well under driver limits
            out.addAll(suppressionRepo.findActiveEmailsAmong(tenantId, lower.subList(i, Math.min(i + 1000, lower.size()))));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public long countActiveSuppressions(TenantId tenantId) {
        return suppressionRepo.countActive(tenantId);
    }

    @Transactional(readOnly = true)
    public Page<MktSuppressionEntry> list(TenantId tenantId, boolean activeOnly, Pageable pageable) {
        return activeOnly ? suppressionRepo.findActiveForTenant(tenantId, pageable)
                          : suppressionRepo.findAllForTenant(tenantId, pageable);
    }

    // ── Suppression ───────────────────────────────────────────────────────────

    /**
     * Idempotent: suppressing an address that is already actively suppressed returns the existing entry
     * and writes no second evidence row.
     */
    @Transactional
    public MktSuppressionEntry suppress(TenantId tenantId, String email, String reason,
                                        String source, String note, UUID by) {
        String e = normalise(email);
        if (e == null || !e.contains("@")) {
            throw new HandyFlowException("Invalid email address", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        if (reason == null || !SUPPRESSION_REASONS.contains(reason)) {
            throw new HandyFlowException("Unknown suppression reason '" + reason + "'",
                    HttpStatus.BAD_REQUEST, "INVALID_SUPPRESSION_REASON");
        }
        int inserted = suppressionRepo.insertIfAbsent(UUID.randomUUID(), tenantId.getValue(), e,
                reason, source, note, by);
        MktSuppressionEntry entry = suppressionRepo.findActive(tenantId, e)
                .orElseThrow(() -> new IllegalStateException("Suppression for " + e + " vanished after insert"));
        if (inserted > 0) {
            evidenceRepo.save(MktConsentEvidence.record(tenantId, null, e, MktConsentEvidence.SUPPRESSION_ADDED,
                    source, clip(reason + (note == null || note.isBlank() ? "" : ": " + note)), by));
            log.info("Suppressed {} (tenant={}, reason={}, source={})", e, tenantId, reason, source);
        }
        return entry;
    }

    /** Lifts one suppression with a documented reason. The contact is NOT re-opted-in. */
    @Transactional
    public MktSuppressionEntry lift(TenantId tenantId, UUID suppressionId, String reason, UUID by) {
        if (reason == null || reason.trim().length() < 10) {
            throw new HandyFlowException("A documented reason of at least 10 characters is required",
                    HttpStatus.BAD_REQUEST, "LIFT_REASON_REQUIRED");
        }
        MktSuppressionEntry entry = suppressionRepo.findByIdAndTenantId(suppressionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Suppression", suppressionId.toString()));
        if (!entry.isActive()) {
            throw new HandyFlowException("This suppression has already been lifted",
                    HttpStatus.CONFLICT, "SUPPRESSION_ALREADY_LIFTED");
        }
        entry.lift(by, reason.trim());
        suppressionRepo.save(entry);
        evidenceRepo.save(MktConsentEvidence.record(tenantId, null, entry.getEmail(),
                MktConsentEvidence.SUPPRESSION_LIFTED, "STAFF", clip(reason.trim()), by));
        log.warn("Suppression lifted for {} (tenant={}, by={})", entry.getEmail(), tenantId, by);
        return entry;
    }

    // ── Consent evidence ──────────────────────────────────────────────────────

    @Transactional
    public void recordOptIn(TenantId tenantId, MktContactPreference pref, String source, String evidence, UUID by) {
        evidenceRepo.save(MktConsentEvidence.record(tenantId, pref.getId(), pref.getEmail(),
                MktConsentEvidence.OPT_IN, source, clip(evidence), by));
    }

    /** Bulk import where the uploader declares they hold permission; the declaration itself is the evidence. */
    @Transactional
    public void recordImportDeclaration(TenantId tenantId, MktContactPreference pref, String source, UUID by) {
        evidenceRepo.save(MktConsentEvidence.record(tenantId, pref.getId(), pref.getEmail(),
                MktConsentEvidence.IMPORT_DECLARATION, source,
                "Uploader declared that this contact has given permission to receive marketing email.", by));
    }

    @Transactional
    public void recordOptOut(TenantId tenantId, MktContactPreference pref, String source) {
        evidenceRepo.save(MktConsentEvidence.record(tenantId, pref.getId(), pref.getEmail(),
                MktConsentEvidence.OPT_OUT, source, null, null));
    }

    @Transactional(readOnly = true)
    public List<MktConsentEvidence> history(TenantId tenantId, String email) {
        String e = normalise(email);
        return e == null ? List.of() : evidenceRepo.findByTenantIdAndEmailOrderByCreatedAtDesc(tenantId, e);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String clip(String s) {
        if (s == null) return null;
        return s.length() > MAX_EVIDENCE_CHARS ? s.substring(0, MAX_EVIDENCE_CHARS) : s;
    }
}
