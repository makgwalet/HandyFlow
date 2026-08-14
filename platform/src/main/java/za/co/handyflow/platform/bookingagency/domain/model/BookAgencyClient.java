package za.co.handyflow.platform.bookingagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A client business the agency manages bookings/scheduling for. Mirrors
 * PayClient/RecAgencyClient's role: the agency's OWN record of a
 * client, not necessarily linked to a HandyFlow tenant at all.
 * <p>
 * DELIBERATELY NO BILLING-RATE FIELD HERE YET — unlike RecAgencyClient's
 * placementFeePct or a Payroll-Bureau-style perEmployeeFee, this
 * module's billing model (flat retainer vs. per-booking fee) hasn't
 * been decided (see this module's own package-info.java). Adding a
 * field now would mean guessing at a shape that might be wrong —
 * better to add it deliberately once the billing layer is actually
 * being built and the real model is known.
 */
@Entity
@Table(name = "booka_agency_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAgencyClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the AGENCY's tenant

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "business_type")
    private String businessType; // free-text description of the client's own business (e.g. "hair salon", "plumber", "medical practice") — informs how the agency's staff should answer calls/book on their behalf

    @Column(name = "timezone")
    private String timezone = "Africa/Johannesburg"; // stored per-client deliberately, not assumed platform-wide — an agency could plausibly serve a client outside SA

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

    @Column(name = "monthly_retainer_amount", precision = 15, scale = 2)
    private java.math.BigDecimal monthlyRetainerAmount;
    // Nullable deliberately — a client onboarded before a rate is
    // negotiated shouldn't block onboarding itself; billing generation
    // will reject with a clear error if this is null when someone tries
    // to invoice, rather than silently charging zero.

    @Version
    private Long version;

    public static BookAgencyClient create(UUID tenantId, String tradingName, String businessType,
                                          String timezone, String contactName, String contactEmail,
                                          String contactPhone) {
        BookAgencyClient c = new BookAgencyClient();
        c.tenantId = tenantId;
        c.tradingName = tradingName.trim();
        c.businessType = businessType;
        c.timezone = timezone != null ? timezone : "Africa/Johannesburg";
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.onboardedAt = LocalDate.now();
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String tradingName, String businessType, String timezone,
                       String contactName, String contactEmail, String contactPhone, String notes) {
        this.tradingName = tradingName.trim();
        this.businessType = businessType;
        this.timezone = timezone;
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

    public void setMonthlyRetainerAmount(java.math.BigDecimal v) {
        this.monthlyRetainerAmount = v;
        this.updatedAt = Instant.now();
    }
}