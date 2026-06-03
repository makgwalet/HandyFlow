package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "AccountantClient")
@Table(name = "acc_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccClient {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "entity_type",  nullable = false) private String entityType;
    @Column(name = "trading_name", nullable = false) private String tradingName;
    @Column(name = "registered_name")                private String registeredName;
    @Column(name = "registration_number")            private String registrationNumber;
    @Column(name = "tax_reference_number")           private String taxReferenceNumber;
    @Column(name = "vat_number")                     private String vatNumber;
    @Column(name = "income_tax_number")              private String incomeTaxNumber;
    @Column(name = "vat_category")                   private String vatCategory;
    @Column(name = "year_end_month", nullable = false) private int yearEndMonth;
    @Column(name = "risk_rating",    nullable = false) private String riskRating = "LOW";
    @Column(name = "fica_completed") private boolean ficaCompleted;
    @Column(name = "fica_completed_date") private LocalDate ficaCompletedDate;
    @Column(name = "sars_agent_appointed") private boolean sarsAgentAppointed;
    @Column(name = "sars_agent_date") private LocalDate sarsAgentDate;
    @Column(name = "tcs_pin")                        private String tcsPin;
    @Column(name = "tcs_pin_expiry")                 private LocalDate tcsPinExpiry;
    @Column(name = "cipc_anniversary_date")          private LocalDate cipcAnniversaryDate;
    @Column(name = "cipc_last_return_date")          private LocalDate cipcLastReturnDate;
    @Column(name = "onboarding_status", nullable = false) private String onboardingStatus = "NEW";
    @Column(name = "crm_customer_id")                private UUID crmCustomerId;
    @Column(name = "linked_tenant_id")               private UUID linkedTenantId;
    @Column(name = "contact_email")                  private String contactEmail;
    @Column(name = "contact_phone")                  private String contactPhone;
    @Column(name = "active")                         private boolean active = true;
    @Column(name = "deleted_at")                     private Instant deletedAt;
    @Column(name = "created_at", updatable = false)  private Instant createdAt;
    @Column(name = "updated_at")                     private Instant updatedAt;
    @Version                                         private Long version;

    public static AccClient create(TenantId tenantId, String entityType, String tradingName,
                                   String registeredName, String registrationNumber,
                                   String taxReferenceNumber, String vatNumber,
                                   String vatCategory, int yearEndMonth,
                                   String contactEmail, String contactPhone) {
        AccClient c = new AccClient();
        c.tenantId            = tenantId;
        c.entityType          = entityType.toUpperCase();
        c.tradingName         = tradingName.trim();
        c.registeredName      = registeredName;
        c.registrationNumber  = registrationNumber;
        c.taxReferenceNumber  = taxReferenceNumber;
        c.vatNumber           = vatNumber;
        c.vatCategory         = vatCategory;
        c.yearEndMonth        = yearEndMonth;
        c.contactEmail        = contactEmail;
        c.contactPhone        = contactPhone;
        c.createdAt           = Instant.now();
        c.updatedAt           = Instant.now();
        return c;
    }

    public void updateRisk(String riskRating) {
        this.riskRating = riskRating;
        this.updatedAt  = Instant.now();
    }

    public void markFicaComplete() {
        this.ficaCompleted     = true;
        this.ficaCompletedDate = LocalDate.now();
        this.updatedAt         = Instant.now();
    }

    public void markSarsAgentAppointed() {
        this.sarsAgentAppointed = true;
        this.sarsAgentDate      = LocalDate.now();
        this.updatedAt          = Instant.now();
    }

    public void updateTcsPin(String pin, LocalDate expiry) {
        this.tcsPin      = pin;
        this.tcsPinExpiry = expiry;
        this.updatedAt   = Instant.now();
    }

    public void setOnboardingStatus(String status) {
        this.onboardingStatus = status;
        this.updatedAt        = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }
}
