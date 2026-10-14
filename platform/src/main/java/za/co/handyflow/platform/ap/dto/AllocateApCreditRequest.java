package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Bills to apply a supplier credit note to. Empty {@code lines} applies it to the supplier's bills, oldest due first. */
public record AllocateApCreditRequest(List<Line> lines) {
    public record Line(UUID billId, BigDecimal amount) {}
}
