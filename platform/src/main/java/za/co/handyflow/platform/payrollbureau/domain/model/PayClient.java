package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A client business the bureau runs payroll for. Mirrors AccClient's
 * role exactly: this is the bureau's OWN record of a client, not
 * necessarily linked to a HandyFlow tenant at all — a client business
 * that only exists as records inside this bureau's portfolio, same as
 * accountant's clients can.
 * <p>
 * Fields deliberately capture what payroll specifically needs that
 * AccClient doesn't: PAYE/UIF/SDL reference numbers (the client's own,
 * distinct from the bureau's), pay frequency, and pay day — the
 * information needed to actually run their payroll and generate their
 * SARS deadlines, mirroring how AccClient carries vatCategory/yearEndMonth
 * for accounting-specific deadline generation.
 */
@Entity
@Table(name = "pay_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the BUREAU's tenant, not the client's

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "paye_reference")
    private String payeReference;

    @Column(name = "uif_reference")
    private String uifReference;

    @Column(name = "sdl_reference")
    private String sdlReference; // null if exempt (payroll below SDL threshold)

    @Column(name = "pay_frequency", nullable = false)
    private String payFrequency = "MONTHLY"; // MONTHLY | WEEKLY | FORTNIGHTLY

    @Column(name = "pay_day")
    private Integer payDay; // day of month for MONTHLY; null for WEEKLY/FORTNIGHTLY

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "onboarded_at")
    private LocalDate onboardedAt;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | OFFBOARDED

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "per_employee_fee", nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal perEmployeeFee = new java.math.BigDecimal("50.00");

    public void setPerEmployeeFee(java.math.BigDecimal v) {
        this.perEmployeeFee = v;
        this.updatedAt = Instant.now();
    }

    @Version
    private Long version;

    public static PayClient create(UUID tenantId, String tradingName, String registrationNumber,
                                   String payeReference, String uifReference, String sdlReference,
                                   String payFrequency, Integer payDay,
                                   String contactName, String contactEmail, String contactPhone) {
        PayClient c = new PayClient();
        c.tenantId = tenantId;
        c.tradingName = tradingName.trim();
        c.registrationNumber = registrationNumber;
        c.payeReference = payeReference;
        c.uifReference = uifReference;
        c.sdlReference = sdlReference;
        c.payFrequency = payFrequency != null ? payFrequency : "MONTHLY";
        c.payDay = payDay;
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.onboardedAt = LocalDate.now();
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String tradingName, String contactName, String contactEmail,
                       String contactPhone, String payFrequency, Integer payDay, String notes) {
        this.tradingName = tradingName.trim();
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.payFrequency = payFrequency;
        this.payDay = payDay;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public void offboard() {
        this.status = "OFFBOARDED";
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