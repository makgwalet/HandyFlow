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

    @Version long version;

    /**
     * FIX §3 + §4: Removed @OneToMany. Parties are loaded via ContractPartyRepository
     * where needed. This avoids the N+1 EAGER fetch and the broken mappedBy reference.
     *
     * The transient list is populated by ContractingService when mapping to ContractResponse.
     */
    @Transient
    private List<ContractParty> parties = new ArrayList<>();

    /** Called by ContractingService after loading parties from ContractPartyRepository. */
    public void setParties(List<ContractParty> parties) {
        this.parties = parties != null ? parties : new ArrayList<>();
    }

    // ── Factory ───────────────────────────────────────────────────────────────

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

    /** Returns true if all parties in the transient list have SIGNED. */
    public boolean allPartiesSigned() {
        return !parties.isEmpty() &&
                parties.stream().allMatch(p -> "SIGNED".equals(p.getSigningStatus()));
    }

    // ── Setters for optional fields ────────────────────────────────────────────

    public void setDates(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate   = endDate;
        this.updatedAt = Instant.now();
    }

    public void setValueAmount(BigDecimal valueAmount) {
        this.valueAmount = valueAmount;
        this.updatedAt   = Instant.now();
    }

    public void setNotes(String notes) {
        this.notes     = notes;
        this.updatedAt = Instant.now();
    }

    public void setAutoRenew(boolean autoRenew, int renewalNoticeDays) {
        this.autoRenew          = autoRenew;
        this.renewalNoticeDays  = renewalNoticeDays;
        this.updatedAt          = Instant.now();
    }
}
