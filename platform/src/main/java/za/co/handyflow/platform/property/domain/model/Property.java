// property/domain/model/Property.java

package za.co.handyflow.platform.property.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "properties")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Property {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "property_type", nullable = false)
    private String propertyType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, String> address;

    private String description;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "market_value", precision = 15, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "photo_url")
    private String photoUrl;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("unitNumber ASC")
    private List<Unit> units = new ArrayList<>();

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

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Extended create — accepts optional financials so the service does not
     * need setters. purchasePrice and marketValue are nullable.
     */
    public static Property create(TenantId tenantId, String name,
                                  String propertyType, Map<String, String> address,
                                  String description, UUID customerId,
                                  BigDecimal purchasePrice, BigDecimal marketValue) {
        Property p = new Property();
        p.tenantId     = tenantId;
        p.name         = name.trim();
        p.propertyType = propertyType.toUpperCase();
        p.address      = address;
        p.description  = description;
        p.customerId   = customerId;
        p.purchasePrice = purchasePrice;
        p.marketValue   = marketValue;
        p.active       = true;
        p.createdAt    = Instant.now();
        p.updatedAt    = Instant.now();
        return p;
    }

    /**
     * Kept for backwards compatibility — callers that don't supply financials.
     * Delegates to the full factory with nulls.
     */
    public static Property create(TenantId tenantId, String name,
                                  String propertyType, Map<String, String> address,
                                  String description, UUID customerId) {
        return create(tenantId, name, propertyType, address, description,
                customerId, null, null);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    /** Update market value — e.g. after a revaluation. */
    public void updateMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
        this.updatedAt   = Instant.now();
    }

    /** Update notes or description after creation. */
    public void updateDetails(String notes, String description) {
        this.notes       = notes;
        this.description = description;
        this.updatedAt   = Instant.now();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
