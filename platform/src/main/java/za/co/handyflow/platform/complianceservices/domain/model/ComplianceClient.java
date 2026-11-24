package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * The client company — resolves the open design question left in this
 * module's own package-info.java. A dedicated entity that OPTIONALLY
 * references an existing CRM customer via {@code crmCustomerId} (a
 * reference, not a copy — enriched with live CRM data at read time, the
 * same pattern {@code TenderPersonnel} already established for HR
 * employee references), rather than extending {@code crm.Customer}
 * itself. Chosen because it doesn't force a schema change onto CRM's own
 * domain model for a still-unproven second module, and a compliance
 * client carries data a sales customer never would — a mandate/
 * authority-to-act, compliance-specific contacts that may differ from
 * whoever CRM's own sales contact is.
 * <p>
 * {@code tenantId} here is the SERVICE PROVIDER's own tenant (the
 * accounting practice, the consultancy) — not the client company's own
 * tenant, if the client company even has a HandyFlow account at all.
 * This entity exists entirely within the service provider's own data,
 * the same way a CRM customer record does.
 */
@Entity
@Table(name = "compliance_service_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "crm_customer_id")
    private UUID crmCustomerId; // nullable — a client company doesn't need to already be a CRM customer

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "mandate_notes", columnDefinition = "TEXT")
    private String mandateNotes;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "trading_name")
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "tax_number")
    private String taxNumber;

    @Column(name = "vat_number")
    private String vatNumber;

    @Column(name = "registered_address", columnDefinition = "TEXT")
    private String registeredAddress;

    @Column(name = "postal_address", columnDefinition = "TEXT")
    private String postalAddress;

    private String industry;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(name = "turnover_band")
    private String turnoverBand;

    @Column(name = "risk_rating")
    private String riskRating;

    @Column(name = "onboarding_status", nullable = false)
    private String onboardingStatus = "NOT_STARTED";

    @Column(name = "financial_year_end_month")
    private Integer financialYearEndMonth;

    @Column(name = "registered_on")
    private java.time.LocalDate registeredOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static ComplianceClient create(TenantId tenantId, String name, UUID crmCustomerId,
                                          String contactEmail, String contactPhone, String mandateNotes, UUID createdBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        ComplianceClient c = new ComplianceClient();
        c.tenantId = tenantId;
        c.name = name;
        c.crmCustomerId = crmCustomerId;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.mandateNotes = mandateNotes;
        c.createdAt = Instant.now();
        c.createdBy = createdBy;
        c.updatedAt = Instant.now();
        c.updatedBy = createdBy;
        return c;
    }

    public void update(String name, String contactEmail, String contactPhone, String mandateNotes, UUID updatedBy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        this.name = name;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.mandateNotes = mandateNotes;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    /** The values for the profile fields, already in the shape the screen sends them. */
    /** Dates some due rules depend on. Either can be cleared by passing null. */
    public void setKeyDates(Integer financialYearEndMonth, java.time.LocalDate registeredOn, java.time.LocalDate today, UUID by) {
        DueDateRules.keyDates(financialYearEndMonth, registeredOn, today);
        this.financialYearEndMonth = financialYearEndMonth;
        this.registeredOn = registeredOn;
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }

    public record Profile(String legalName, String tradingName, String registrationNumber, String entityType,
                          String taxNumber, String vatNumber, String registeredAddress, String postalAddress,
                          String industry, Integer employeeCount, String turnoverBand, String riskRating,
                          String onboardingStatus) {}

    /**
     * Sets the profile and returns the names of the tracked facts that changed, so the caller can
     * restart the source record for exactly those.
     */
    public java.util.List<String> updateProfile(Profile p, UUID updatedBy) {
        String legal = ClientProfileRules.clean(p.legalName(), 255);
        String reg = ClientProfileRules.registrationNumber(p.registrationNumber());
        String type = ClientProfileRules.requireOneOf("entityType", p.entityType(), ClientProfileRules.ENTITY_TYPES);
        String tax = ClientProfileRules.taxNumber(p.taxNumber());
        String vat = ClientProfileRules.vatNumber(p.vatNumber());
        String regAddr = ClientProfileRules.clean(p.registeredAddress(), 1000);
        String industry = ClientProfileRules.clean(p.industry(), 120);
        Integer emp = ClientProfileRules.employeeCount(p.employeeCount());
        String band = ClientProfileRules.requireOneOf("turnoverBand", p.turnoverBand(), ClientProfileRules.TURNOVER_BANDS);
        String risk = ClientProfileRules.requireOneOf("riskRating", p.riskRating(), ClientProfileRules.RISK_RATINGS);
        String onboarding = p.onboardingStatus() == null || p.onboardingStatus().isBlank() ? this.onboardingStatus
                : ClientProfileRules.requireOneOf("onboardingStatus", p.onboardingStatus(),
                        java.util.Set.copyOf(ClientProfileRules.ONBOARDING_STATUSES));

        java.util.List<String> changed = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(legal, legalName)) changed.add("legalName");
        if (!java.util.Objects.equals(reg, registrationNumber)) changed.add("registrationNumber");
        if (!java.util.Objects.equals(type, entityType)) changed.add("entityType");
        if (!java.util.Objects.equals(tax, taxNumber)) changed.add("taxNumber");
        if (!java.util.Objects.equals(vat, vatNumber)) changed.add("vatNumber");
        if (!java.util.Objects.equals(regAddr, registeredAddress)) changed.add("registeredAddress");
        if (!java.util.Objects.equals(industry, this.industry)) changed.add("industry");
        if (!java.util.Objects.equals(emp, employeeCount)) changed.add("employeeCount");
        if (!java.util.Objects.equals(band, turnoverBand)) changed.add("turnoverBand");

        this.legalName = legal;
        this.tradingName = ClientProfileRules.clean(p.tradingName(), 255);
        this.registrationNumber = reg;
        this.entityType = type;
        this.taxNumber = tax;
        this.vatNumber = vat;
        this.registeredAddress = regAddr;
        this.postalAddress = ClientProfileRules.clean(p.postalAddress(), 1000);
        this.industry = industry;
        this.employeeCount = emp;
        this.turnoverBand = band;
        this.riskRating = risk;
        this.onboardingStatus = onboarding;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
        return changed;
    }

    public void deactivate(UUID updatedBy) {
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public void reactivate(UUID updatedBy) {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
