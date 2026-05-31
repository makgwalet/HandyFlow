package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.UUID;
public record CreateLeaseRequest(
        @NotBlank String lesseeName, String lesseeIdNumber,
        String lesseeEmail, String lesseePhone,
        UUID customerId,
        @NotNull LocalDate startDate, LocalDate endDate,
        @NotNull BigDecimal monthlyRent, BigDecimal depositAmount,
        Integer paymentDay, BigDecimal escalationRate
) {}