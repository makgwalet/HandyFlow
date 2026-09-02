package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An external client organization purchasing training services —
 * NOT necessarily a HandyFlow tenant itself. Its delegates
 * ({@link TrainProvDelegate}) are the people this client nominates to
 * attend sessions.
 */
@Entity
@Table(name = "trainprov_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvClient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

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

    /** ACTIVE | INACTIVE */
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static TrainProvClient create(TenantId tenantId, String clientCode, String tradingName,
                                          String registrationNumber, String contactName, String contactEmail,
                                          String contactPhone, String address) {
        TrainProvClient c = new TrainProvClient();
        c.tenantId = tenantId.getValue();
        c.clientCode = clientCode;
        c.tradingName = tradingName;
        c.registrationNumber = registrationNumber;
        c.contactName = contactName;
        c.contactEmail = contactEmail;
        c.contactPhone = contactPhone;
        c.address = address;
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String tradingName, String registrationNumber, String contactName,
                        String contactEmail, String contactPhone, String address) {
        this.tradingName = tradingName;
        this.registrationNumber = registrationNumber;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.status = "INACTIVE";
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
        return this.deletedAt != null;
    }
}
