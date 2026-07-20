package za.co.handyflow.platform.accountant.dto;

import java.util.List;

public record BulkDeadlineGenerationResponse(
        int totalClients,
        int succeeded,
        List<String> failures
) {
}