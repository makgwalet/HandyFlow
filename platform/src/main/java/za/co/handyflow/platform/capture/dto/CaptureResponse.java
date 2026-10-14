package za.co.handyflow.platform.capture.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Suggestions only. Any field may be null; {@code notes} says what could not be read. */
public record CaptureResponse(
        boolean textFound,
        String supplierName,
        String documentNumber,
        LocalDate documentDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal vatAmount,
        String supplierVatNumber,
        List<String> notes
) {}
