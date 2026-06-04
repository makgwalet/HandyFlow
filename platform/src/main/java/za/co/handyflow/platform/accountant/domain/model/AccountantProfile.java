package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accountant_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountantProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "firm_name",           nullable = false) private String     firmName;
    @Column(name = "practice_number")                       private String     practiceNumber;
    @Column(name = "registration_number")                   private String     registrationNumber;
    @Column(name = "vat_number")                            private String     vatNumber;
    @Column(name = "contact_email",       nullable = false) private String     contactEmail;
    @Column(name = "contact_phone")                         private String     contactPhone;
    @Column(name = "vat_category")                          private String     vatCategory;
    @Column(name = "default_hourly_rate", nullable = false) private BigDecimal defaultHourlyRate;
    @Column(name = "year_end_month",      nullable = false) private int        yearEndMonth;
    @Column(name = "created_at",          updatable = false) private Instant   createdAt;
    @Column(name = "updated_at")                            private Instant    updatedAt;

    public static AccountantProfile create(TenantId tenantId, String firmName,
                                           String practiceNumber, String vatNumber,
                                           String contactEmail, String contactPhone,
                                           BigDecimal defaultHourlyRate, int yearEndMonth) {
        AccountantProfile p = new AccountantProfile();
        p.tenantId         = tenantId;
        p.firmName         = firmName;
        p.practiceNumber   = practiceNumber;
        p.vatNumber        = vatNumber;
        p.contactEmail     = contactEmail;
        p.contactPhone     = contactPhone;
        p.defaultHourlyRate = defaultHourlyRate != null ? defaultHourlyRate : new BigDecimal("750");
        p.yearEndMonth     = yearEndMonth;
        p.createdAt        = Instant.now();
        p.updatedAt        = Instant.now();
        return p;
    }

    public void update(String firmName, String practiceNumber, String vatNumber,
                       String contactEmail, String contactPhone,
                       BigDecimal defaultHourlyRate, int yearEndMonth) {
        if (firmName != null)         this.firmName         = firmName;
        if (practiceNumber != null)   this.practiceNumber   = practiceNumber;
        if (vatNumber != null)        this.vatNumber        = vatNumber;
        if (contactEmail != null)     this.contactEmail     = contactEmail;
        if (contactPhone != null)     this.contactPhone     = contactPhone;
        if (defaultHourlyRate != null)this.defaultHourlyRate = defaultHourlyRate;
        if (yearEndMonth > 0)         this.yearEndMonth     = yearEndMonth;
        this.updatedAt = Instant.now();
    }

    @PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }
}
