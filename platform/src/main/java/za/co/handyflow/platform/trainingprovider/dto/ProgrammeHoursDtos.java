package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProgrammeHoursDtos {
    private ProgrammeHoursDtos() {
    }

    public record HoursRequest(@NotNull BigDecimal hours, String covers, @NotBlank String sourceType, @NotBlank String sourceReference, LocalDate sourceVersionDate,
                               @NotBlank String sourceSection, @NotNull LocalDate effectiveDate, @NotBlank String verifiedBy, @NotNull LocalDate verifiedOn) {
    }

    public record WithdrawRequest(@NotBlank String reason) {
    }

    public record HoursView(UUID id, UUID programmeVersionId, BigDecimal hours, String covers, String sourceType, String sourceReference, LocalDate sourceVersionDate,
                            String sourceSection, LocalDate effectiveDate, String verifiedBy, LocalDate verifiedOn, String status, String withdrawnReason, Instant updatedAt) {
    }

    /** What a programme version records. No hours means the programme does not record any, not that zero are needed. */
    public record VersionHours(UUID programmeVersionId, int versionNo, String versionStatus, boolean editable, HoursView current, List<HoursView> history) {
    }
}
