package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.LocalDate;
public record RecordPaymentRequest(
        @NotNull BigDecimal amountPaid, @NotNull LocalDate paidDate,
        String paymentMethod, String reference
) {}
