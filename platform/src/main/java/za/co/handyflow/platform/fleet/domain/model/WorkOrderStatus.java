package za.co.handyflow.platform.fleet.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Work order lifecycle, with explicit legal transitions (same pattern as {@link VehicleStatus}).
 * <pre>
 * OPEN ─▶ QUOTED ─▶ APPROVED ─▶ IN_PROGRESS ─▶ COMPLETED
 *   │        │  ▲ (reject)          ⇅ ON_HOLD
 *   └────────┴──────────────────────────▶ CANCELLED
 * OPEN ─▶ IN_PROGRESS directly is allowed only when there is no quotation (in-house work).
 * </pre>
 * COMPLETED and CANCELLED are terminal: costs have been booked and the vehicle released.
 */
public enum WorkOrderStatus {
    OPEN, QUOTED, APPROVED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED;

    /** Still live — neither finished nor cancelled. */
    public static final List<WorkOrderStatus> ACTIVE = List.of(OPEN, QUOTED, APPROVED, IN_PROGRESS, ON_HOLD);
    /** The vehicle is (or is being held) in the workshop. */
    public static final List<WorkOrderStatus> WORKING = List.of(IN_PROGRESS, ON_HOLD);

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED = new EnumMap<>(WorkOrderStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(QUOTED, IN_PROGRESS, CANCELLED));
        ALLOWED.put(QUOTED, EnumSet.of(APPROVED, OPEN, CANCELLED));
        ALLOWED.put(APPROVED, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(ON_HOLD, COMPLETED, CANCELLED));
        ALLOWED.put(ON_HOLD, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(WorkOrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    public boolean canTransitionTo(WorkOrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}