package za.co.handyflow.platform.clinic.dto.billing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordPaymentRequest(
        @NotNull UUID patientId,
        String method,        // CASH | CARD | EFT | SCHEME_EFT | MEDICAL_AID — defaults to CASH if omitted
        @NotNull @Positive BigDecimal amount,
        String reference,
        String notes
) {}