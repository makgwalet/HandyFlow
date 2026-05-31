// security/domain/model/Site.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "security_sites")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Site {

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> address;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String instructions;

    @Column(name = "qr_secret", nullable = false)
    private String qrSecret;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<Checkpoint> checkpoints = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "contract_status")
    private String contractStatus = "ACTIVE"; // ACTIVE | EXPIRING_SOON | EXPIRED | TERMINATED

    @Column(name = "contract_start")
    private java.time.LocalDate contractStart;

    @Column(name = "contract_end")
    private java.time.LocalDate contractEnd;

    @Column(name = "termination_reason")
    private String terminationReason;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Version
    private Long version;

    public static Site create(TenantId tenantId, UUID customerId, String name,
                              Map<String, String> address, BigDecimal latitude,
                              BigDecimal longitude, String contactName,
                              String contactPhone, String instructions) {
        Site s = new Site();
        s.tenantId      = tenantId;
        s.customerId    = customerId;
        s.name          = name.trim();
        s.address       = address;
        s.latitude      = latitude;
        s.longitude     = longitude;
        s.contactName   = contactName;
        s.contactPhone  = contactPhone;
        s.instructions  = instructions;
        s.qrSecret      = UUID.randomUUID().toString().replace("-", "");
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

    public void terminateContract(String reason) {
        this.contractStatus  = "TERMINATED";
        this.terminationReason = reason;
        this.terminatedAt    = Instant.now();
        this.active          = false;
    }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}