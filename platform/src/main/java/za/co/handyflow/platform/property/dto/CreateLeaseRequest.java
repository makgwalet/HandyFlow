package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.UUID;
public record CreateLeaseRequest(
        UUID        customerId,
        @NotBlank String      lesseeName,
        String      lesseeIdNumber,
        String      lesseeEmail,
        String      lesseePhone,
        @NotNull  LocalDate   startDate,
        LocalDate   endDate,                    // null = month-to-month
        @NotNull  BigDecimal  monthlyRent,
        @NotNull  BigDecimal  depositAmount,
        @Min(1) @jakarta.validation.constraints.Max(31)
        int         paymentDay,
        BigDecimal  escalationRate,             // annual % e.g. 8.5
        String      notes
) {}