package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors Accounting's AgingReportResponse (AR side) deliberately — same
 * bucket names (CURRENT, 1-30, 31-60, 61-90, 90+), same shape — for
 * consistency across the app. Sourced from ApBill instead of Invoice.
 * <p>
 * Only APPROVED and OVERDUE bills are included, matching exactly the
 * status filter ApBillRepository.sumOutstanding() already uses for "AP
 * outstanding" elsewhere in this module — not a new definition. DRAFT
 * bills aren't outstanding payables yet; SECOND_APPROVAL bills are
 * excluded too, same as they already are from the AP summary's own
 * outstanding total, for the same reason (not yet cleared for payment).
 */
public record ApAgingReportResponse(
        LocalDate asAt,
        List<AgingLine> lines,
        BigDecimal current,
        BigDecimal days1to30,
        BigDecimal days31to60,
        BigDecimal days61to90,
        BigDecimal over90,
        BigDecimal total
) {
    public record AgingLine(
            UUID billId,
            String billNumber,
            String supplierName,
            LocalDate dueDate,
            int daysOverdue,
            BigDecimal balance,
            String bucket
    ) {}
}