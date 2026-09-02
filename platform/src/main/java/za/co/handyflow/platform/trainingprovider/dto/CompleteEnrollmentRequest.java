package za.co.handyflow.platform.trainingprovider.dto;

import java.math.BigDecimal;

public record CompleteEnrollmentRequest(
        BigDecimal score,
        boolean passed
) {}
