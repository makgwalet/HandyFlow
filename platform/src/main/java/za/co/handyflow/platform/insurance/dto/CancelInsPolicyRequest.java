package za.co.handyflow.platform.insurance.dto;

import java.time.LocalDate;

public record CancelInsPolicyRequest(
        LocalDate cancelledDate, // null = today
        String reason
) {}
