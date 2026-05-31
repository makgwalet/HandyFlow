package za.co.handyflow.platform.identity.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.identity.TenantCreatedEvent;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// WHY protected constructor?
// JPA needs a no-arg constructor to instantiate entities via reflection.
// Making it protected prevents accidental direct instantiation —
// force callers to use our factory method which enforces business rules.
public class Tenant extends AggregateRoot<Tenant> {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    // ← NO @Enumerated here
    @Column(nullable = false, unique = true)
    private String email;

    // @Enumerated ONLY on status — nowhere else
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    public enum TenantStatus {
        TRIAL, ACTIVE, SUSPENDED, CANCELLED
    }

    @Column(name = "vat_number")
    private String vatNumber;

    private String phone;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> address;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "trading_name")
    private String tradingName;

    // WHY a static factory method instead of a public constructor?
    // Factory methods have NAMES — "register" tells you the intent.
    // They can enforce business rules before the object exists.
    // They can register domain events as part of creation.
    public static Tenant register(String name, String slug, String email, java.util.List<String> moduleKeys) {
        validateSlug(slug);

        Tenant tenant = new Tenant();

        tenant.initTenantId(TenantId.of(tenant.getId()));
        tenant.name = name;
        tenant.slug = slug.toLowerCase().trim();
        tenant.email = email.toLowerCase().trim();
        tenant.status = TenantStatus.TRIAL;

        // Register domain event — Spring Data publishes this after save()
        tenant.registerEvent(TenantCreatedEvent.of(tenant.getTenantId(), name, email, tenant.getId(), moduleKeys));

        return tenant;
    }

    public void activate() {
        if (this.status == TenantStatus.CANCELLED){
            throw new IllegalStateException("Cannot activate a cancelled tenant");
        }
        this.status = TenantStatus.ACTIVE;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public boolean isActive() {
        return this.status == TenantStatus.ACTIVE ||
                this.status == TenantStatus.TRIAL;
    }

    private static void validateSlug(String slug) {
        if (slug == null || !slug.matches("^[a-z0-9-]{3,100}$")) {
            throw new IllegalArgumentException(
                    "Slug must be 3-100 characters, lowercase letters, numbers and hyphens only"
            );
        }
    }

    public void updateCompanyDetails(String vatNumber, String phone, String email,
                                     Map<String, String> address, String bankName,
                                     String bankAccount, String bankBranch,
                                     String paymentTerms) {
        this.vatNumber    = vatNumber;
        this.phone        = phone;
        this.email        = email;
        this.address      = address;
        this.bankName     = bankName;
        this.bankAccount  = bankAccount;
        this.bankBranch   = bankBranch;
        this.paymentTerms = paymentTerms;
        //this.updatedAt    = Instant.now();
    }

    public void updateProfile(String name, String phone, String vatNumber,
                              Map<String, String> address, String bankName,
                              String bankAccount, String bankBranch,
                              String paymentTerms) {
        if (name != null && !name.isBlank()) this.name = name;
        if (phone != null)        this.phone = phone;
        if (vatNumber != null)    this.vatNumber = vatNumber;
        if (address != null)      this.address = address;
        if (bankName != null)     this.bankName = bankName;
        if (bankAccount != null)  this.bankAccount = bankAccount;
        if (bankBranch != null)   this.bankBranch = bankBranch;
        if (paymentTerms != null) this.paymentTerms = paymentTerms;
        //this.updatedAt = Instant.now();
    }

    public void updateLogo(String logoUrl) {
        this.logoUrl = logoUrl;
        //this.updatedAt = Instant.now();
    }
}

