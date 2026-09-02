package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A planting season/year a farm defines for itself — "2026 Summer Season",
 * "2025/26 Season" — the temporal grouping every {@link AgCropCycle} on
 * that farm optionally belongs to, enabling season-level reporting (a
 * "Farm P&L" for one season, per the architecture plan's §3/§37 reasoning:
 * this module supplies the cost side, {@code invoicing} the revenue side).
 * Farm-scoped rather than tenant-wide — different farms under one tenant
 * may run different climates/seasons independently.
 */
@Entity
@Table(name = "ag_seasons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgSeason {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private String status = "PLANNING"; // PLANNING | ACTIVE | CLOSED

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static AgSeason create(TenantId tenantId, UUID farmId, String name, LocalDate startDate,
                                   LocalDate endDate, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (farmId == null) throw new IllegalArgumentException("farmId is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (startDate == null) throw new IllegalArgumentException("startDate is required");
        if (endDate != null && endDate.isBefore(startDate)) throw new IllegalArgumentException("endDate must not be before startDate");

        AgSeason s = new AgSeason();
        s.tenantId = tenantId;
        s.farmId = farmId;
        s.name = name;
        s.startDate = startDate;
        s.endDate = endDate;
        s.notes = notes;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(String name, LocalDate startDate, LocalDate endDate, String notes) {
        if (name != null && !name.isBlank()) this.name = name;
        if (startDate != null) this.startDate = startDate;
        this.endDate = endDate;
        this.notes = notes;
    }

    public void activate() { this.status = "ACTIVE"; }

    public void close() { this.status = "CLOSED"; }

    public void softDelete() { this.deletedAt = Instant.now(); }

    public boolean isDeleted() { return deletedAt != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
