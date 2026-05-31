package za.co.handyflow.platform.ap.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateBillRequest(
        String     supplierName,
        String     billNumber,
        LocalDate  billDate,
        LocalDate  dueDate,
        String     category,
        String     description,
        BigDecimal amount,
        BigDecimal vatAmount,
        String     notes
) {}
