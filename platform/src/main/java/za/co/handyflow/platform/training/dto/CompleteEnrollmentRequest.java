package za.co.handyflow.platform.training.dto;

import java.math.BigDecimal;

public record CompleteEnrollmentRequest(
        BigDecimal score,
        boolean passed
) {}
