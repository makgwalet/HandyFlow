package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/** Pure rules for work orders and maintenance plans (patch E2). */
public final class WorkOrderRules {
    private WorkOrderRules() {}

    public static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    public static final Set<String> LINE_KINDS = Set.of("PART", "LABOUR", "OTHER");
    public static final Set<String> TRIGGERS = Set.of("HOURS", "DAYS");
    /** Repairs and component work need a recorded test result before they can be completed. */
    public static final Set<String> NEEDS_TEST_RESULT = Set.of("REPAIR", "ELECTRICAL", "HYDRAULICS", "ENGINE", "TRACKS");

    public static void checkCreate(String title, String type, String priority, LocalDate due, LocalDate today) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A work order needs a title.");
        EarthmovingRules.oneOf(type, EarthmovingRules.MAINTENANCE_TYPES, "Work order type");
        EarthmovingRules.oneOf(priority == null || priority.isBlank() ? "NORMAL" : priority, PRIORITIES, "Priority");
        if (due != null && due.isBefore(today.minusYears(1)))
            throw new IllegalArgumentException("The due date " + due + " is more than a year in the past.");
    }

    public static void checkMove(WorkOrderStatus from, WorkOrderStatus to) {
        if (!from.canMoveTo(to))
            throw new IllegalStateException("A " + from.name().toLowerCase().replace('_', ' ') + " work order cannot become "
                    + to.name().toLowerCase().replace('_', ' ') + ".");
    }

    public static void checkLine(String kind, String description, BigDecimal quantity, BigDecimal unitCost) {
        EarthmovingRules.oneOf(kind, LINE_KINDS, "Line kind");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("A line needs a description.");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Quantity must be more than zero.");
        if (unitCost == null || unitCost.signum() < 0) throw new IllegalArgumentException("Unit cost cannot be negative.");
    }

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitCost) {
        return quantity.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Completion needs a record of the work, and a test result for repairs. SERVICE needs the meter reading. */
    public static void checkComplete(String type, String workDone, String testResult, BigDecimal meterHours,
                                     BigDecimal downtimeHours, BigDecimal labourHours) {
        EarthmovingRules.requireReason(workDone, "the work done");
        if (NEEDS_TEST_RESULT.contains(type)) EarthmovingRules.requireReason(testResult, "the test result of a " + type.toLowerCase());
        if ("SERVICE".equals(type) && meterHours == null)
            throw new IllegalArgumentException("A service needs the hour meter reading it was done at.");
        if (meterHours != null && meterHours.signum() < 0) throw new IllegalArgumentException("Meter hours cannot be negative.");
        if (downtimeHours != null && downtimeHours.signum() < 0) throw new IllegalArgumentException("Downtime cannot be negative.");
        if (labourHours != null && labourHours.signum() < 0) throw new IllegalArgumentException("Labour hours cannot be negative.");
    }

    public static void checkPlan(String name, String taskType, String trigger, Integer interval, Integer lead) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A plan needs a name.");
        EarthmovingRules.oneOf(taskType, EarthmovingRules.MAINTENANCE_TYPES, "Task type");
        EarthmovingRules.oneOf(trigger, TRIGGERS, "Trigger");
        if (interval == null || interval <= 0) throw new IllegalArgumentException("The interval must be more than zero.");
        if (lead == null || lead < 0) throw new IllegalArgumentException("The warning lead cannot be negative.");
        if (lead >= interval) throw new IllegalArgumentException("The warning lead must be shorter than the interval.");
    }

    // ── plan state ───────────────────────────────────────────────────────────

    public enum PlanState { OK, DUE_SOON, DUE, OVERDUE }

    public record PlanStatus(PlanState state, BigDecimal used, BigDecimal remaining) {}

    /**
     * Where a plan stands. Hours: operating hours since it was last done. Days: days since. DUE at the interval,
     * OVERDUE once it is more than 10% past, DUE_SOON within the warning lead. With no "last done" baseline the plan
     * starts counting from the baseline given (the machine's hours or the plan's creation date), never from zero.
     */
    public static PlanStatus evaluate(String trigger, int interval, int lead, BigDecimal baselineHours, LocalDate baselineDate,
                                      BigDecimal currentHours, LocalDate today) {
        BigDecimal used;
        if ("HOURS".equals(trigger)) {
            BigDecimal cur = currentHours == null ? BigDecimal.ZERO : currentHours;
            BigDecimal base = baselineHours == null ? BigDecimal.ZERO : baselineHours;
            used = cur.subtract(base).max(BigDecimal.ZERO);
        } else {
            used = baselineDate == null ? BigDecimal.ZERO
                    : BigDecimal.valueOf(java.time.temporal.ChronoUnit.DAYS.between(baselineDate, today)).max(BigDecimal.ZERO);
        }
        BigDecimal iv = BigDecimal.valueOf(interval);
        BigDecimal remaining = iv.subtract(used);
        PlanState s;
        if (used.compareTo(iv.multiply(new BigDecimal("1.10"))) > 0) s = PlanState.OVERDUE;
        else if (used.compareTo(iv) >= 0) s = PlanState.DUE;
        else if (remaining.compareTo(BigDecimal.valueOf(lead)) <= 0 && lead > 0) s = PlanState.DUE_SOON;
        else s = PlanState.OK;
        return new PlanStatus(s, used, remaining);
    }
}
