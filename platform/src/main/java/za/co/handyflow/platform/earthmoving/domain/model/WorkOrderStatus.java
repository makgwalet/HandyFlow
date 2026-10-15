package za.co.handyflow.platform.earthmoving.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Work order lifecycle. Completed and cancelled are final. A job must be started before it can be completed. */
public enum WorkOrderStatus {
    OPEN, IN_PROGRESS, AWAITING_PARTS, COMPLETED, CANCELLED;

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> NEXT = new EnumMap<>(WorkOrderStatus.class);
    static {
        NEXT.put(OPEN, EnumSet.of(IN_PROGRESS, CANCELLED));
        NEXT.put(IN_PROGRESS, EnumSet.of(AWAITING_PARTS, COMPLETED, CANCELLED));
        NEXT.put(AWAITING_PARTS, EnumSet.of(IN_PROGRESS, CANCELLED));
        NEXT.put(COMPLETED, EnumSet.noneOf(WorkOrderStatus.class));
        NEXT.put(CANCELLED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    public boolean canMoveTo(WorkOrderStatus target) { return NEXT.get(this).contains(target); }

    /** Still open: not completed and not cancelled. */
    public boolean isOpen() { return this != COMPLETED && this != CANCELLED; }
}
