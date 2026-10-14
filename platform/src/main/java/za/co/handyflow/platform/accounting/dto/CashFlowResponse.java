package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Cash flow statement, indirect method (finance patch F13). {@code difference} is not zero when the ledger and the statement disagree. */
public record CashFlowResponse(
        LocalDate fromDate, LocalDate toDate, BigDecimal netProfit,
        List<Line> operating, BigDecimal operatingTotal,
        List<Line> investing, BigDecimal investingTotal,
        List<Line> financing, BigDecimal financingTotal,
        BigDecimal netChange, BigDecimal openingCash, BigDecimal closingCash,
        BigDecimal difference, boolean agrees
) {
    public record Line(String label, BigDecimal amount) {}
}
