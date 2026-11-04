package za.co.handyflow.platform.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PayRunTaxYearDtos {
    private PayRunTaxYearDtos() {}

    /** One run in the preview. {@code verdict} is CORRECT, WRONG_LABEL or NEEDS_REVIEW. */
    public record Row(UUID id, String payRunNumber, LocalDate payDate, String status, boolean finalisedOrPaid,
                      int storedYear, String storedLabel, int proposedYear, String proposedLabel,
                      String verdict, String reason, int payslips, int payslipsOnOtherYear) {}

    public record Preview(int total, int correct, int wrongLabel, int needsReview, List<Row> rows) {}

    public record ApplyRequest(@NotEmpty List<UUID> payRunIds, @NotBlank String reason) {}

    public record Applied(String migrationRef, int corrected, int skipped, List<String> skippedReasons) {}
}
