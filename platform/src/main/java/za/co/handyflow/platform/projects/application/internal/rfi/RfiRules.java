package za.co.handyflow.platform.projects.application.internal.rfi;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Pure RFI rules — no Spring, no database, no clock (today is always passed in).
 *
 * CLOSURE: an RFI whose answer affects schedule or cost must not simply be closed and forgotten.
 * It may be closed only if (a) it is linked to a change order that is still alive (DRAFT, SUBMITTED
 * or APPROVED — a REJECTED or WITHDRAWN change order handles nothing), or (b) someone has recorded
 * why no change is needed. An RFI with no flagged impact closes freely.
 *
 * ESCALATION: an unanswered (SUBMITTED) RFI past its due date is escalated at three levels —
 * 1 = at least a day overdue, 2 = a week, 3 = two weeks. Each level fires once.
 */
public final class RfiRules {

    private RfiRules() {}

    public static final int LEVEL_1_DAYS = 1;
    public static final int LEVEL_2_DAYS = 7;
    public static final int LEVEL_3_DAYS = 14;

    public static Optional<String> closureBlocker(boolean impactsSchedule, boolean impactsCost,
                                                  String linkedChangeOrderStatus, String noChangeNote) {
        if (!impactsSchedule && !impactsCost) return Optional.empty();

        boolean handled = linkedChangeOrderStatus != null
                && !"REJECTED".equals(linkedChangeOrderStatus)
                && !"WITHDRAWN".equals(linkedChangeOrderStatus);
        if (handled) return Optional.empty();
        if (noChangeNote != null && !noChangeNote.isBlank()) return Optional.empty();

        String what = impactsSchedule && impactsCost ? "schedule and cost"
                : impactsSchedule ? "the schedule" : "cost";
        String why = linkedChangeOrderStatus == null
                ? "has no change order handling it"
                : "is linked to a " + linkedChangeOrderStatus.toLowerCase() + " change order, which does not handle it";
        return Optional.of("This RFI is flagged as affecting " + what + " but " + why
                + " — link a live change order, or record why no change is needed");
    }

    /** 0 = not overdue; otherwise 1–3. */
    public static int escalationLevel(LocalDate dueDate, LocalDate today) {
        int days = daysOverdue(dueDate, today);
        if (days >= LEVEL_3_DAYS) return 3;
        if (days >= LEVEL_2_DAYS) return 2;
        if (days >= LEVEL_1_DAYS) return 1;
        return 0;
    }

    /** Whole days past the due date; 0 if not yet overdue or no due date. */
    public static int daysOverdue(LocalDate dueDate, LocalDate today) {
        if (dueDate == null || !today.isAfter(dueDate)) return 0;
        return (int) ChronoUnit.DAYS.between(dueDate, today);
    }

    /** Ageing bucket for an RFI that has been open this many days. */
    public static String ageBucket(long daysOpen) {
        if (daysOpen <= 7)  return "0-7";
        if (daysOpen <= 14) return "8-14";
        if (daysOpen <= 30) return "15-30";
        return "30+";
    }
}