package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An external client organization the FM company services. Soft-deletable,
 * matching every sibling provider module's own client entity shape.
 */
@Entity
@Table(name = "fm_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_code", nullable = false)
    private String clientCode;

    @Column(name = "trading_name", nullable = false)
    private String tradingName;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "contact_name")
    private String contactName;
    @Column(name = "contact_email")
    private String contactEmail;
    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static FmClient create(TenantId tenantId, String clientCode, String tradingName,
                                   String registrationNumber, String contactName, String contactEmail,
                                   String contactPhone, String address) {
        FmClient c = new FmClient();
        c.tenantId = tenantId;
        c.clientCode = clientCode;
        c.tradingName = tradingName;
        c.registrationNumber = registrationNumber;
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.address = address;
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String tradingName, String registrationNumber, String contactName,
                        String contactEmail, String contactPhone, String address) {
        if (tradingName != null) this.tradingName = tradingName;
        this.registrationNumber = registrationNumber;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.updatedAt = Instant.now();
    }

    public void deactivate() { this.status = "INACTIVE"; this.updatedAt = Instant.now(); }
    public void reactivate() { this.status = "ACTIVE"; this.updatedAt = Instant.now(); }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.status = "INACTIVE";
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isActive() { return "ACTIVE".equals(status); }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
