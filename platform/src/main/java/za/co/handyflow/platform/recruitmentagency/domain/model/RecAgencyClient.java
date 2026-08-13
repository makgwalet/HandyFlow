package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A client business the agency finds and places candidates for. Mirrors
 * PayClient's role: the agency's OWN record of a client, not necessarily
 * linked to a HandyFlow tenant at all.
 * <p>
 * placementFeePct is a per-client OVERRIDE of the agency's default rate
 * (RecAgencyProfile.defaultPlacementFeePct) — null means "use the
 * agency default"; set means this specific client negotiated a
 * different rate. This is the field that makes the placement-fee
 * billing model genuinely different from Payroll Bureau's flat
 * per-employee rate — a real client-specific negotiation point in
 * recruitment agency contracts, not something to flatten away.
 */
@Entity
@Table(name = "reca_agency_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the AGENCY's tenant

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "industry")
    private String industry;

    @Column(name = "placement_fee_pct", precision = 5, scale = 2)
    private BigDecimal placementFeePct; // null = use agency default

    @Column(name = "guarantee_period_days")
    private Integer guaranteePeriodDays; // typically 60-90 days — if the
    // placed candidate leaves within
    // this window, many agency
    // contracts require a free
    // replacement or partial refund.
    // Captured now as data; the
    // actual guarantee-tracking
    // WORKFLOW is not built in this
    // foundation layer — flagged as
    // real follow-up work, not
    // silently assumed handled.

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "onboarded_at")
    private LocalDate onboardedAt;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | INACTIVE

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static RecAgencyClient create(UUID tenantId, String tradingName, String registrationNumber,
                                         String industry, BigDecimal placementFeePct,
                                         Integer guaranteePeriodDays, String contactName,
                                         String contactEmail, String contactPhone) {
        RecAgencyClient c = new RecAgencyClient();
        c.tenantId = tenantId;
        c.tradingName = tradingName.trim();
        c.registrationNumber = registrationNumber;
        c.industry = industry;
        c.placementFeePct = placementFeePct;
        c.guaranteePeriodDays = guaranteePeriodDays;
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.onboardedAt = LocalDate.now();
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String tradingName, String industry, BigDecimal placementFeePct,
                       Integer guaranteePeriodDays, String contactName, String contactEmail,
                       String contactPhone, String notes) {
        this.tradingName = tradingName.trim();
        this.industry = industry;
        this.placementFeePct = placementFeePct;
        this.guaranteePeriodDays = guaranteePeriodDays;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}