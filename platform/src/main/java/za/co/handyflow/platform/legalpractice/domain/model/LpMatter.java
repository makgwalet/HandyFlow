package za.co.handyflow.platform.legalpractice.domain.model;

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
 * The central tracking unit of the module — one legal matter for one
 * client. Carries its own billing type (FIXED_FEE or HOURLY, set once at
 * creation — confirmed via AskUserQuestion), independent of whether the
 * client also holds an {@code LpRetainerAgreement}. Full lifecycle state
 * machine: OPEN -> ON_HOLD -> OPEN (reopen) -> CLOSED -> ARCHIVED.
 * <p>
 * Billable-work guards (can't log time/disbursements on a CLOSED or
 * ARCHIVED matter) are enforced in the service layer against this
 * entity's status, the same separation-of-concerns split
 * {@code AgMovementRecordService} uses relative to {@code AgAnimal} —
 * this entity only tracks its own state, callers are responsible for
 * checking it before acting.
 */
@Entity
@Table(name = "lp_matters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpMatter {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "attorney_id", nullable = false)
    private UUID attorneyId; // the responsible attorney

    @Column(name = "matter_number", nullable = false)
    private String matterNumber;

    @Column(name = "matter_type", nullable = false, length = 30)
    private String matterType; // LITIGATION | CONVEYANCING | ESTATES | CORPORATE_COMMERCIAL | FAMILY_LAW | CRIMINAL_DEFENSE | LABOUR | OTHER

    @Column(name = "matter_name", nullable = false)
    private String matterName;

    private String description;

    @Column(name = "billing_type", nullable = false, length = 20)
    private String billingType; // FIXED_FEE | HOURLY

    @Column(name = "fixed_fee_amount", precision = 15, scale = 2)
    private BigDecimal fixedFeeAmount; // required when billingType = FIXED_FEE, enforced in the service layer

    @Column(nullable = false, length = 20)
    private String status; // OPEN | ON_HOLD | CLOSED | ARCHIVED

    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpMatter create(TenantId tenantId, UUID clientId, UUID attorneyId, String matterNumber,
                                   String matterType, String matterName, String description,
                                   String billingType, BigDecimal fixedFeeAmount,
                                   LocalDate openedDate, String notes) {
        LpMatter m = new LpMatter();
        m.tenantId = tenantId;
        m.clientId = clientId;
        m.attorneyId = attorneyId;
        m.matterNumber = matterNumber;
        m.matterType = matterType;
        m.matterName = matterName;
        m.description = description;
        m.billingType = billingType;
        m.fixedFeeAmount = fixedFeeAmount;
        m.status = "OPEN";
        m.openedDate = openedDate != null ? openedDate : LocalDate.now();
        m.notes = notes;
        m.createdAt = Instant.now();
        m.updatedAt = Instant.now();
        return m;
    }

    public void update(UUID attorneyId, String matterName, String description,
                        String billingType, BigDecimal fixedFeeAmount, String notes) {
        this.attorneyId = attorneyId;
        this.matterName = matterName;
        this.description = description;
        this.billingType = billingType;
        this.fixedFeeAmount = fixedFeeAmount;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void putOnHold() {
        if (!"OPEN".equals(this.status)) {
            throw new IllegalStateException("Only an OPEN matter can be put on hold, current status: " + this.status);
        }
        this.status = "ON_HOLD";
        this.updatedAt = Instant.now();
    }

    public void reopen() {
        if (!"ON_HOLD".equals(this.status) && !"CLOSED".equals(this.status)) {
            throw new IllegalStateException("Only an ON_HOLD or CLOSED matter can be reopened, current status: " + this.status);
        }
        this.status = "OPEN";
        this.closedDate = null;
        this.updatedAt = Instant.now();
    }

    public void close(LocalDate closedDate) {
        if ("ARCHIVED".equals(this.status)) {
            throw new IllegalStateException("An ARCHIVED matter cannot be closed again");
        }
        this.status = "CLOSED";
        this.closedDate = closedDate != null ? closedDate : LocalDate.now();
        this.updatedAt = Instant.now();
    }

    public void archive() {
        if (!"CLOSED".equals(this.status)) {
            throw new IllegalStateException("Only a CLOSED matter can be archived, current status: " + this.status);
        }
        this.status = "ARCHIVED";
        this.updatedAt = Instant.now();
    }

    /** ON_HOLD deliberately does NOT count as billable — that's the point of putting a matter on hold. */
    public boolean isBillable() {
        return "OPEN".equals(this.status);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
