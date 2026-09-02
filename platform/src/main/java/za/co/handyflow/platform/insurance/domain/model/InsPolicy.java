package za.co.handyflow.platform.insurance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single insurance policy the tenant holds, on its OWN business asset or
 * as general cover, with a third-party insurer (and, optionally, through a
 * broker). This is a tenant recording insurance it ALREADY HAS — not a
 * quote/bind workflow — so a newly-created policy starts life {@code
 * ACTIVE} directly, matching how a tenant would actually use this screen
 * (capturing a policy schedule they already hold, not originating cover).
 * <p>
 * Lifecycle: {@code ACTIVE -> LAPSED -> ACTIVE} (reinstate), {@code ACTIVE
 * / LAPSED -> CANCELLED}, {@code ACTIVE / LAPSED -> EXPIRED} (system-driven,
 * via {@code InsNotificationScheduler}'s daily sweep, once {@code
 * expiryDate} has passed with no renewal recorded), {@code ACTIVE / LAPSED
 * -> RENEWED} (set on THIS row the moment a successor policy is created
 * via {@code InsPolicyService.renew()} — {@code RENEWED} and {@code
 * EXPIRED} are both terminal outcomes for a given policy row, distinguished
 * so reporting can tell "lapsed without renewal" apart from "rolled
 * forward into a new term"). {@code CANCELLED} is always terminal.
 * <p>
 * {@code renewalOfPolicyId} is a self-reference (nullable — most policies
 * are standalone, or the tenant's first-ever capture of a policy that was
 * already renewed once in the real world before HandyFlow existed) forming
 * a simple renewal chain, one row per term, the same one-row-per-term shape
 * {@code AgCropCycle}/{@code AgBreedingRecord} already use for their own
 * repeatable, dated cycles.
 * <p>
 * {@code assetType}/{@code assetReference} are plain freeform fields, NOT a
 * foreign key into {@code fleet}/{@code earthmoving} — see this module's
 * own {@code package-info.java} for why.
 */
@Entity
@Table(name = "ins_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InsPolicy {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(name = "insurer_name", nullable = false)
    private String insurerName;

    @Column(name = "line_of_business", nullable = false, length = 20)
    private String lineOfBusiness; // MOTOR | PROPERTY | EQUIPMENT | LIABILITY | OTHER

    @Column(name = "asset_type", length = 20)
    private String assetType; // VEHICLE | PROPERTY | EQUIPMENT | OTHER | null

    @Column(name = "asset_reference")
    private String assetReference; // freeform — e.g. "Toyota Hilux CA123456" or "Warehouse, 12 Main Rd"

    @Column(name = "sum_insured", precision = 15, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "premium_frequency", nullable = false, length = 15)
    private String premiumFrequency; // MONTHLY | QUARTERLY | ANNUAL

    @Column(name = "excess_amount", precision = 12, scale = 2)
    private BigDecimal excessAmount;

    @Column(name = "broker_or_insurer_contact")
    private String brokerOrInsurerContact;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, length = 20)
    private String status; // ACTIVE | LAPSED | CANCELLED | EXPIRED | RENEWED

    @Column(name = "renewal_of_policy_id")
    private UUID renewalOfPolicyId;

    @Column(name = "cancelled_date")
    private LocalDate cancelledDate;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /** Set the moment {@code InsNotificationScheduler} last sent an expiring-soon reminder — prevents re-notifying every day within the lookahead window. */
    @Column(name = "expiry_reminder_sent_at")
    private Instant expiryReminderSentAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static InsPolicy create(TenantId tenantId, String policyNumber, String insurerName,
                                    String lineOfBusiness, String assetType, String assetReference,
                                    BigDecimal sumInsured, BigDecimal premiumAmount, String premiumFrequency,
                                    BigDecimal excessAmount, String brokerOrInsurerContact,
                                    LocalDate startDate, LocalDate expiryDate, String notes,
                                    UUID renewalOfPolicyId) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (policyNumber == null || policyNumber.isBlank()) throw new IllegalArgumentException("policyNumber is required");
        if (insurerName == null || insurerName.isBlank()) throw new IllegalArgumentException("insurerName is required");
        if (lineOfBusiness == null || lineOfBusiness.isBlank()) throw new IllegalArgumentException("lineOfBusiness is required");
        if (premiumAmount == null) throw new IllegalArgumentException("premiumAmount is required");
        if (premiumFrequency == null || premiumFrequency.isBlank()) throw new IllegalArgumentException("premiumFrequency is required");
        if (startDate == null) throw new IllegalArgumentException("startDate is required");
        if (expiryDate == null) throw new IllegalArgumentException("expiryDate is required");
        if (!expiryDate.isAfter(startDate)) throw new IllegalArgumentException("expiryDate must be after startDate");

        InsPolicy p = new InsPolicy();
        p.tenantId = tenantId;
        p.policyNumber = policyNumber.trim();
        p.insurerName = insurerName.trim();
        p.lineOfBusiness = lineOfBusiness;
        p.assetType = assetType;
        p.assetReference = assetReference;
        p.sumInsured = sumInsured;
        p.premiumAmount = premiumAmount;
        p.premiumFrequency = premiumFrequency;
        p.excessAmount = excessAmount;
        p.brokerOrInsurerContact = brokerOrInsurerContact;
        p.startDate = startDate;
        p.expiryDate = expiryDate;
        p.notes = notes;
        p.renewalOfPolicyId = renewalOfPolicyId;
        p.status = "ACTIVE";
        p.createdAt = Instant.now();
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(String insurerName, String assetType, String assetReference, BigDecimal sumInsured,
                        BigDecimal premiumAmount, String premiumFrequency, BigDecimal excessAmount,
                        String brokerOrInsurerContact, LocalDate startDate, LocalDate expiryDate, String notes) {
        requireMutable();
        if (insurerName != null && !insurerName.isBlank()) this.insurerName = insurerName.trim();
        this.assetType = assetType;
        this.assetReference = assetReference;
        this.sumInsured = sumInsured;
        if (premiumAmount != null) this.premiumAmount = premiumAmount;
        if (premiumFrequency != null && !premiumFrequency.isBlank()) this.premiumFrequency = premiumFrequency;
        this.excessAmount = excessAmount;
        this.brokerOrInsurerContact = brokerOrInsurerContact;
        if (startDate != null) this.startDate = startDate;
        if (expiryDate != null) this.expiryDate = expiryDate;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void cancel(LocalDate cancelledDate, String reason) {
        if (!"ACTIVE".equals(status) && !"LAPSED".equals(status)) {
            throw new IllegalStateException("Only an ACTIVE or LAPSED policy can be cancelled (was " + status + ")");
        }
        this.status = "CANCELLED";
        this.cancelledDate = cancelledDate != null ? cancelledDate : LocalDate.now();
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markLapsed() {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Only an ACTIVE policy can be marked LAPSED (was " + status + ")");
        }
        this.status = "LAPSED";
        this.updatedAt = Instant.now();
    }

    public void reinstate() {
        if (!"LAPSED".equals(status)) {
            throw new IllegalStateException("Only a LAPSED policy can be reinstated (was " + status + ")");
        }
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    /** System-driven — called only by {@code InsNotificationScheduler}'s sweep once {@code expiryDate} has passed. */
    public void markExpired() {
        if (!"ACTIVE".equals(status) && !"LAPSED".equals(status)) return; // already terminal, no-op
        this.status = "EXPIRED";
        this.updatedAt = Instant.now();
    }

    /** Called by {@code InsPolicyService.renew()} on the OLD row once the new successor row has been created. */
    public void markRenewed() {
        requireMutable();
        this.status = "RENEWED";
        this.updatedAt = Instant.now();
    }

    public void markExpiryReminderSent() {
        this.expiryReminderSentAt = Instant.now();
    }

    public boolean isRenewable() {
        return "ACTIVE".equals(status) || "LAPSED".equals(status);
    }

    private void requireMutable() {
        if ("CANCELLED".equals(status) || "EXPIRED".equals(status) || "RENEWED".equals(status)) {
            throw new IllegalStateException("Policy " + policyNumber + " is " + status + " and can no longer be changed");
        }
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
