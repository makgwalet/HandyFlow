package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalSummaryResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Pure arithmetic behind the client summary, so it is testable without a database. */
public final class FmPortalSummary {

    private static final Set<String> FINISHED = Set.of("COMPLETED", "CANCELLED", "DECLINED");

    private FmPortalSummary() {}

    /** @param statusCounts raw work-order status -> count (as stored, not as presented) */
    public static FmPortalSummaryResponse build(Map<String, Long> statusCounts, Collection<FmWorkOrder> activeWork,
                                                Collection<FmInvoice> invoices, Instant now, LocalDate today) {
        Map<String, Long> presented = new TreeMap<>();
        long open = 0;
        for (Map.Entry<String, Long> e : statusCounts.entrySet()) {
            String p = FmPortalStatus.present(e.getKey());
            presented.merge(p, e.getValue(), Long::sum);
            if (!FINISHED.contains(p)) open += e.getValue();
        }
        long atRisk = 0, breached = 0;
        for (FmWorkOrder w : activeWork) {
            String s = FmPortalSla.state(w, now);
            if ("AT_RISK".equals(s)) atRisk++;
            else if ("BREACHED".equals(s) && w.getCompletedAt() == null) breached++;
        }
        BigDecimal outstanding = BigDecimal.ZERO, overdueBal = BigDecimal.ZERO;
        long overdue = 0;
        for (FmInvoice i : invoices) {
            BigDecimal bal = i.balance();
            if (bal == null || bal.signum() <= 0) continue;
            outstanding = outstanding.add(bal);
            if (i.getDueDate() != null && i.getDueDate().isBefore(today)) {
                overdue++;
                overdueBal = overdueBal.add(bal);
            }
        }
        return new FmPortalSummaryResponse(presented, open, atRisk, breached, outstanding, overdue, overdueBal);
    }
}
