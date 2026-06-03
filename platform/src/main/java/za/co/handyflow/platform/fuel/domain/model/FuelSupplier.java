// fuel/domain/model/FuelSupplier.java

package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_suppliers")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelSupplier {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "account_number")
    private String accountNumber;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    public static FuelSupplier create(TenantId tenantId, String name,
                                      String contactName, String contactPhone,
                                      String contactEmail, String accountNumber) {
        FuelSupplier s = new FuelSupplier();
        s.tenantId      = tenantId;
        s.name          = name.trim();
        s.contactName   = contactName;
        s.contactPhone  = contactPhone;
        s.contactEmail  = contactEmail;
        s.accountNumber = accountNumber;
        s.active        = true;
        s.createdAt     = Instant.now();
        s.updatedAt     = Instant.now();
        return s;
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public void update(String name, String contactName, String contactPhone,
                       String contactEmail, String accountNumber) {
        this.name          = name;
        this.contactName   = contactName;
        this.contactPhone  = contactPhone;
        this.contactEmail  = contactEmail;
        this.accountNumber = accountNumber;
        this.updatedAt     = java.time.Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}