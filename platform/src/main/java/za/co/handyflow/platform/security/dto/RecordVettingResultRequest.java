package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record RecordVettingResultRequest(
        @NotBlank String result,    // CLEAR | HIT | INCONCLUSIVE
        String conductedBy,
        LocalDate conductedAt,
        LocalDate nextReviewAt,
        String reportRef,
        String notes
) {}
