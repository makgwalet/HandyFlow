package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A credit note received from a supplier. {@code billId} is optional; with it the credit is applied to that bill when issued. */
public record CreateApCreditNoteRequest(
        String     creditNoteNumber,
        String     supplierName,
        UUID       billId,
        String     category,
        LocalDate  creditDate,
        String     reason,
        BigDecimal amount,
        BigDecimal vatAmount
) {}
