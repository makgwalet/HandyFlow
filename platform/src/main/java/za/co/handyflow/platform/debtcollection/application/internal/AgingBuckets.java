package za.co.handyflow.platform.debtcollection.application.internal;

import java.util.List;

/** Aging bucket boundaries for the collections dashboard. Pure logic, kept separate so it is trivially testable. */
final class AgingBuckets {

    /** Not-yet-due invoices fall into the first bucket: a case's exposure is shown even before an invoice goes overdue. */
    static final List<String> LABELS = List.of("0-30", "31-60", "61-90", "91-120", "121+");

    private AgingBuckets() {}

    static int indexFor(long daysOverdue) {
        if (daysOverdue <= 30) return 0;
        if (daysOverdue <= 60) return 1;
        if (daysOverdue <= 90) return 2;
        if (daysOverdue <= 120) return 3;
        return 4;
    }
}
