package za.co.handyflow.platform.clinic.dto.billing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordPaymentRequest(
        @NotNull UUID patientId,
        @NotNull String method,          // CASH, EFT, CARD — informational only
        @NotNull @Positive BigDecimal amount,
        String reference,
        String notes,
        UUID bankAccountId               // NEW — nullable; see class Javadoc
) {}