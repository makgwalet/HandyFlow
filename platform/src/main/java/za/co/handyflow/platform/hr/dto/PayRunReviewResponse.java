package za.co.handyflow.platform.hr.dto;

import java.util.List;
import java.util.UUID;

/** What a checker should look at before approving, with errors that block approval and warnings that do not. */
public record PayRunReviewResponse(
        UUID runId, String runNumber, String status, String comparedWith, boolean blocking, int errors, int warnings,
        List<Item> items) {

    public record Item(String severity, String code, String employee, String message) {}
}
