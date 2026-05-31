package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.LocalDate;
public record CreatePaymentRequest(
        @NotNull Integer periodYear, @NotNull Integer periodMonth,
        @NotNull BigDecimal amountDue, @NotNull LocalDate dueDate
) {}