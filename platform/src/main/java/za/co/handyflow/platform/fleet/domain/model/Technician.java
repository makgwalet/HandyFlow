package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A workshop technician. Deactivated rather than deleted, so historical work orders keep their reference. */
@Entity
@Table(name = "fleet_technicians")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Technician {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String email;

    /** Default rate for labour lines logged against this technician. */
    @Column(name = "hourly_rate", precision = 15, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static Technician create(TenantId tenantId, String name, String phone, String email, BigDecimal hourlyRate) {
        Technician t = new Technician();
        t.tenantId = tenantId;
        t.createdAt = Instant.now();
        t.update(name, phone, email, hourlyRate, true);
        return t;
    }

    public void update(String name, String phone, String email, BigDecimal hourlyRate, boolean active) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.hourlyRate = hourlyRate;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}