package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A quality-assurance authority the provider deals with. The provider names its own; none are pre-filled. Retired, never deleted. */
@Entity(name = "TrainProvAuthority")
@Table(name = "trainprov_authorities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAuthority {

    public static final Set<String> TYPES = Set.of("QCTO", "SETA", "SAQA", "DHET", "UMALUSI", "CHE", "PROFESSIONAL_BODY", "OTHER");

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String name;
    @Column(name = "authority_type", nullable = false)
    private String authorityType;
    private String notes;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAuthority create(TenantId tenantId, String name, String type, String notes) {
        TrainProvAuthority a = new TrainProvAuthority();
        a.tenantId = tenantId.getValue();
        a.apply(name, type, notes);
        a.createdAt = Instant.now();
        return a;
    }

    public void update(String name, String type, String notes) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("A retired authority cannot be changed");
        }
        apply(name, type, notes);
    }

    private void apply(String name, String type, String notes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Authority name is required");
        }
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException("Authority type must be one of " + String.join(", ", new java.util.TreeSet<>(TYPES)));
        }
        this.name = name.trim();
        this.authorityType = type;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = Instant.now();
    }

    public void retire(String reason, UUID by) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This authority is already retired");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Say why, in at least 10 characters");
        }
        this.status = "RETIRED";
        this.retiredReason = reason.trim();
        this.retiredBy = by;
        this.retiredAt = Instant.now();
        this.updatedAt = this.retiredAt;
    }
}
