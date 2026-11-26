package za.co.handyflow.platform.payrollbureau.dto;

import java.util.List;
import java.util.UUID;

/** What a person should look at before calculating a run (DRAFT) or before approving it (otherwise). */
public record PayRunReviewResponse(
        UUID payRunId, String status, String previousRunNumber, int blocking, int warnings, List<Item> findings
) {
    public record Item(String severity, String code, UUID employeeId, String employeeName, String message) {}
}
