package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.LocalDate;
public record CreatePaymentRequest(
        @NotNull int         periodYear,
        @NotNull int         periodMonth,   // 1–12
        @NotNull BigDecimal  amountDue,
        @NotNull LocalDate   dueDate,
        String               notes
) {}
