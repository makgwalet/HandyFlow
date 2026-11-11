package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fuel_customer_sites")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelCustomerSite {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "delivery_instructions", length = 500)
    private String deliveryInstructions;

    @Column(name = "operating_hours", length = 100)
    private String operatingHours;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    public static FuelCustomerSite create(TenantId tenantId, UUID accountId, String name, String address, String contactName,
                                          String contactPhone, String instructions, String hours) {
        FuelCustomerSite s = new FuelCustomerSite();
        s.tenantId = tenantId; s.accountId = accountId; s.createdAt = Instant.now();
        s.update(name, address, contactName, contactPhone, instructions, hours);
        return s;
    }

    public void update(String name, String address, String contactName, String contactPhone, String instructions, String hours) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A site needs a name.");
        this.name = name.trim(); this.address = clean(address); this.contactName = clean(contactName);
        this.contactPhone = clean(contactPhone); this.deliveryInstructions = clean(instructions); this.operatingHours = clean(hours);
        this.updatedAt = Instant.now();
    }

    public void softDelete() { this.deletedAt = Instant.now(); this.active = false; this.updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
