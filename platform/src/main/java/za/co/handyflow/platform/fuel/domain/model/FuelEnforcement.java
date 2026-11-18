package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

/** One control's rollout state for a company. OFF -> MONITOR -> PILOT (a set of depots) -> ENFORCED; any state can be rolled back to a lower one. */
@Entity
@Table(name = "fuel_enforcement")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelEnforcement {

    public static final String ARRIVAL_CHECK = "ARRIVAL_CHECK", POD_POLICY = "POD_POLICY";
    public static final Set<String> CONTROLS = Set.of(ARRIVAL_CHECK, POD_POLICY);
    public static final List<String> ORDER = List.of("OFF", "MONITOR", "PILOT", "ENFORCED");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, updatable = false, length = 30)
    private String control;

    @Column(nullable = false, length = 12)
    private String state = "OFF";

    @Column(name = "pilot_depot_ids")
    private String pilotDepotIds;

    @Column(length = 500)
    private String reason;

    @Column(name = "state_since", nullable = false)
    private Instant stateSince = Instant.now();

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "version_no", nullable = false)
    private int versionNo = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static FuelEnforcement off(TenantId tenantId, String control) {
        FuelEnforcement e = new FuelEnforcement();
        e.tenantId = tenantId; e.control = control;
        return e;
    }

    public static int rank(String state) { return ORDER.indexOf(state); }

    public Set<UUID> pilotDepots() {
        Set<UUID> out = new LinkedHashSet<>();
        if (pilotDepotIds != null) for (String p : pilotDepotIds.split(",")) if (!p.isBlank()) out.add(UUID.fromString(p.trim()));
        return out;
    }

    public void change(String newState, Collection<UUID> depots, String reason, UUID by, Instant now) {
        if (rank(newState) < 0) throw new IllegalArgumentException("State must be one of OFF, MONITOR, PILOT or ENFORCED.");
        if (!newState.equals(this.state)) this.stateSince = now;
        this.state = newState;
        this.pilotDepotIds = "PILOT".equals(newState) && depots != null && !depots.isEmpty() ? String.join(",", depots.stream().map(UUID::toString).toList()) : null;
        this.reason = reason == null ? null : reason.trim();
        this.changedBy = by; this.versionNo++; this.updatedAt = now;
    }
}
