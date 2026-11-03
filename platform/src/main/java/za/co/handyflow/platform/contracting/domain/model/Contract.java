package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fixes applied:
 *
 * §2  @NoArgsConstructor now uses AccessLevel.PROTECTED — JPA only needs protected,
 *     not public. The factory method (create()) is the only valid construction path.
 *
 * §3  parties @OneToMany changed to FetchType.LAZY — the original EAGER caused N+1
 *     queries: a paginated list of 20 contracts fired 20 extra party queries.
 *     The service explicitly loads parties where needed.
 *
 * §4  mappedBy "contractId" is a UUID column, not a JPA relationship field, so the
 *     @OneToMany mapping is removed from the entity entirely. ContractPartyRepository
 *     provides findByContract(contractId) for when parties are needed. This also
 *     removes the CascadeType.ALL which was accidentally deleting party records.
 *
 * *** IMPORTANT — READ BEFORE CALLING getParties() ***
 * parties is @Transient and starts as an EMPTY list on every entity loaded by
 * any repository query. It is ONLY populated if something explicitly calls
 * setParties(...) after separately querying ContractPartyRepository. Calling
 * getParties() on a Contract you loaded yourself (e.g. in a scheduler, not via
 * ContractingService) without first calling setParties() will silently iterate
 * nothing — this is exactly what broke ContractExpiryScheduler's renewal
 * reminder emails (they were never actually sent, despite logging success).
 *
 * NEW: reminder30SentAt/reminder14SentAt/reminder7SentAt/reminder1SentAt —
 * added by V56 specifically so ContractExpiryScheduler could track which
 * renewal reminders have already fired and self-heal if a scheduled run is
 * missed, but the scheduler never actually read or wrote them (it relied
 * purely on an exact end_date match instead, which can't catch up after a
 * missed day). See markReminderSent()/isReminderSent() below — these are
 * what the scheduler should have been using from the start.
 */
@Entity
@Table(name = "contracts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Contract {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID      tenantId;
    @Column(name = "contract_number") String    contractNumber;
    @Column(name = "template_id")     UUID      templateId;
    String    title;
    @Column(name = "contract_type")   String    contractType;
    String    status;
    @Column(columnDefinition = "TEXT") String   body;
    @Column(name = "value_amount")    BigDecimal valueAmount;
    String    currency = "ZAR";
    @Column(name = "start_date")      LocalDate startDate;
    @Column(name = "end_date")        LocalDate endDate;
    @Column(name = "auto_renew")      boolean   autoRenew;
    @Column(name = "renewal_notice_days") int   renewalNoticeDays = 30;
    String    notes;
    @Column(name = "sent_at")         Instant   sentAt;
    @Column(name = "signed_at")       Instant   signedAt;
    @Column(name = "terminated_at")   Instant   terminatedAt;
    @Column(name = "termination_reason") String terminationReason;
    @Column(name = "created_by")      UUID      createdBy;
    @Column(name = "created_at")      Instant   createdAt;
    @Column(name = "updated_at")      Instant   updatedAt;
    @Column(name = "deleted_at")      Instant   deletedAt;
    @Column(name = "body_hash")       String    bodyHash;
    @Column(name = "body_locked_at")  Instant   bodyLockedAt;

    /** The business record this contract is for (for example a hire deployment) and the contract it amends. All optional. */
    @Column(name = "parent_contract_id") UUID parentContractId;
    @Column(name = "ref_type")        String    refType;
    @Column(name = "ref_id")          UUID      refId;

    // NEW — see class Javadoc. One column per threshold rather than a single
    // "last reminder sent" field, because each threshold must fire exactly
    // once independently (a contract could plausibly need its 30-day AND
    // 7-day reminder in the same scheduler run if it was only just created
    // with 6 days left on it — each is a distinct notification, not a
    // replacement for the other).
    @Column(name = "reminder_30_sent_at") Instant reminder30SentAt;
    @Column(name = "reminder_14_sent_at") Instant reminder14SentAt;
    @Column(name = "reminder_7_sent_at")  Instant reminder7SentAt;
    @Column(name = "reminder_1_sent_at")  Instant reminder1SentAt;

    // ── Executed copy, retention and disposal (V581) ──────────────────────────
    /** The signed contract rendered once and kept in the document store. Written once; the database refuses a replacement. */
    @Column(name = "executed_storage_key") String  executedStorageKey;
    @Column(name = "executed_sha256")      String  executedSha256;
    @Column(name = "executed_size_bytes")  Long    executedSizeBytes;
    @Column(name = "executed_stored_at")   Instant executedStoredAt;
    /** Set when the retention clock was started in the records retention engine. */
    @Column(name = "retention_clock_at")   Instant retentionClockAt;
    /** Set when a disposal approved by two people deleted the executed copy and cleared the text. Final. */
    @Column(name = "disposed_at")          Instant disposedAt;
    @Column(name = "disposal_batch_id")    UUID    disposalBatchId;

    @Version long version;

    @Transient
    private List<ContractParty> parties = new ArrayList<>();

    /** Called by ContractingService after loading parties from ContractPartyRepository. */
    public void setParties(List<ContractParty> parties) {
        this.parties = parties != null ? parties : new ArrayList<>();
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /** Signed, whatever happened after: these have an executed version that can be shared and must be kept. */
    public static final java.util.Set<String> EXECUTED = java.util.Set.of("SIGNED", "EXPIRED", "TERMINATED", "ARCHIVED");
    /** No longer in force: the retention clock runs from the day it ended. */
    public static final java.util.Set<String> ENDED = java.util.Set.of("EXPIRED", "TERMINATED", "ARCHIVED");

    public boolean isExecuted() { return EXECUTED.contains(status) && deletedAt == null; }
    public boolean isEnded() { return ENDED.contains(status) && deletedAt == null; }
    public boolean isDisposed() { return disposedAt != null; }
    public boolean hasExecutedCopy() { return executedStorageKey != null; }

    /** The day the contract stopped being in force: termination, else its end date, else its last change (SAST). */
    public java.time.LocalDate endedOn() {
        java.time.ZoneId z = java.time.ZoneId.of("Africa/Johannesburg");
        if (terminatedAt != null) return terminatedAt.atZone(z).toLocalDate();
        if (endDate != null) return endDate;
        return (updatedAt != null ? updatedAt : Instant.now()).atZone(z).toLocalDate();
    }

    public void recordExecutedCopy(String storageKey, String sha256, long sizeBytes) {
        if (!isExecuted()) throw new IllegalStateException("Only a signed contract has an executed copy");
        if (executedStorageKey != null) throw new IllegalStateException("The executed copy is already stored");
        this.executedStorageKey = storageKey;
        this.executedSha256 = sha256;
        this.executedSizeBytes = sizeBytes;
        this.executedStoredAt = Instant.now();
    }

    public void markRetentionClockStarted() { if (retentionClockAt == null) this.retentionClockAt = Instant.now(); }

    /**
     * Disposal approved under the records retention rules: the contract text is replaced by a note, the record (number, title,
     * parties, dates, body hash, executed-copy hash) stays. Only for a contract that is no longer in force.
     */
    public void dispose(UUID batchId) {
        if (!isEnded()) throw new IllegalStateException("Only a contract that has ended can be disposed of");
        if (disposedAt != null) throw new IllegalStateException("This contract was already disposed of");
        this.disposedAt = Instant.now();
        this.disposalBatchId = batchId;
        this.body = "[The text of this contract was disposed of under the records retention policy on "
                + java.time.LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"))
                + ". The contract number, parties, dates and hashes are kept as the record.]";
        this.updatedAt = this.disposedAt;
    }

    public static Contract create(TenantId tenantId, String contractNumber,
                                  String title, String contractType,
                                  String body, UUID templateId, UUID createdBy) {
        Contract c = new Contract();
        c.id                = UUID.randomUUID();
        c.tenantId          = tenantId.getValue();
        c.contractNumber    = contractNumber;
        c.title             = title;
        c.contractType      = contractType;
        c.body              = body;
        c.templateId        = templateId;
        c.createdBy         = createdBy;
        c.status            = "DRAFT";
        c.currency          = "ZAR";
        c.autoRenew         = false;
        c.renewalNoticeDays = 30;
        c.createdAt         = Instant.now();
        c.updatedAt         = Instant.now();
        return c;
    }

    // ── State transitions ─────────────────────────────────────────────────────

    public void submitForReview() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT contracts can be submitted for review");
        this.status    = "UNDER_REVIEW";
        this.updatedAt = Instant.now();
    }

    public void send() {
        if (!List.of("DRAFT", "UNDER_REVIEW").contains(status))
            throw new IllegalStateException("Contract must be DRAFT or UNDER_REVIEW to send");
        this.status    = "SENT";
        this.sentAt    = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markSigned() {
        this.status    = "SIGNED";
        this.signedAt  = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void expire() {
        this.status    = "EXPIRED";
        this.updatedAt = Instant.now();
    }

    public void terminate(String reason) {
        if (!"SIGNED".equals(status))
            throw new IllegalStateException("Only SIGNED contracts can be terminated");
        this.status            = "TERMINATED";
        this.terminatedAt      = Instant.now();
        this.terminationReason = reason;
        this.updatedAt         = Instant.now();
    }

    public void archive() {
        this.status    = "ARCHIVED";
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT contracts can be deleted");
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void lockBody(String sha256Hash) {
        this.bodyHash     = sha256Hash;
        this.bodyLockedAt = Instant.now();
    }

    /**
     * NEW: body previously had no mutator at all after create() — this is
     * what makes it possible to fill in remaining {{template}} variables
     * (or make any other body edit) on a contract that's still DRAFT or
     * UNDER_REVIEW. Guarded by assertEditable(), same convention as
     * terminate()/send()/submitForReview() — the business rule for when a
     * contract may be edited lives here on the entity, not scattered into
     * the service.
     */
    public void updateBody(String body) {
        assertEditable();
        this.body      = body;
        this.updatedAt = Instant.now();
    }

    /**
     * A contract may only be edited (body, dates, value, notes, auto-renew)
     * while it's still DRAFT or UNDER_REVIEW. Once sendForSigning() locks
     * the body (lockBody()) the bodyHash exists specifically to detect
     * tampering — allowing further edits past that point would defeat the
     * point of that hash. Safe to add to the existing setDates()/
     * setValueAmount()/setNotes()/setAutoRenew() setters below with zero
     * behavior change: every one of their real call sites only ever runs
     * during createContract(), on a brand-new contract that is always
     * DRAFT at that point — verified against every setter call site in
     * ContractingService.java before adding this.
     */
    private void assertEditable() {
        if (!List.of("DRAFT", "UNDER_REVIEW").contains(status))
            throw new IllegalStateException(
                    "Only DRAFT or UNDER_REVIEW contracts can be edited (current status: " + status + ")");
    }

    /**
     * Returns true if all parties in the transient list have SIGNED.
     * NOTE: relies on the transient parties list actually being populated —
     * see class Javadoc. Only meaningful when called on a Contract that went
     * through ContractingService's normal load path.
     */
    public boolean allPartiesSigned() {
        return !parties.isEmpty() &&
                parties.stream().allMatch(p -> "SIGNED".equals(p.getSigningStatus()));
    }

    // ── Renewal reminder tracking ────────────────────────────────────────────

    /** True if the reminder for this specific threshold has already been sent. */
    public boolean isReminderSent(int thresholdDays) {
        return switch (thresholdDays) {
            case 30 -> reminder30SentAt != null;
            case 14 -> reminder14SentAt != null;
            case 7  -> reminder7SentAt != null;
            case 1  -> reminder1SentAt != null;
            default -> throw new IllegalArgumentException("No reminder tracking for threshold: " + thresholdDays);
        };
    }

    public void markReminderSent(int thresholdDays) {
        Instant now = Instant.now();
        switch (thresholdDays) {
            case 30 -> reminder30SentAt = now;
            case 14 -> reminder14SentAt = now;
            case 7  -> reminder7SentAt = now;
            case 1  -> reminder1SentAt = now;
            default -> throw new IllegalArgumentException("No reminder tracking for threshold: " + thresholdDays);
        }
        this.updatedAt = now;
    }

    // ── Setters for optional fields ────────────────────────────────────────────

    public void setDates(LocalDate startDate, LocalDate endDate) {
        assertEditable();
        this.startDate = startDate;
        this.endDate   = endDate;
        this.updatedAt = Instant.now();
    }

    public void setValueAmount(BigDecimal valueAmount) {
        assertEditable();
        this.valueAmount = valueAmount;
        this.updatedAt   = Instant.now();
    }

    public void setNotes(String notes) {
        assertEditable();
        this.notes     = notes;
        this.updatedAt = Instant.now();
    }

    public void setAutoRenew(boolean autoRenew, int renewalNoticeDays) {
        assertEditable();
        this.autoRenew          = autoRenew;
        this.renewalNoticeDays  = renewalNoticeDays;
        this.updatedAt          = Instant.now();
    }

    /** Records what this contract is for and, for an addendum, the contract it amends. */
    public void linkReference(String refType, UUID refId, UUID parentContractId) {
        this.refType = refType;
        this.refId = refId;
        this.parentContractId = parentContractId;
        this.updatedAt = Instant.now();
    }
}
