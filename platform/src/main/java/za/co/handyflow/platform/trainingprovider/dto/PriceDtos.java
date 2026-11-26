package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PriceDtos {
    private PriceDtos() {
    }

    public record PriceRequest(@NotNull UUID programmeVersionId, UUID funderId, @NotBlank String feeType, String description, @NotNull BigDecimal amount,
                               @NotNull Boolean includesVat, @NotNull LocalDate validFrom, LocalDate validTo, @NotBlank String sourceNote) {
    }

    public record WithdrawRequest(@NotBlank String reason) {
    }

    public record PriceView(UUID id, UUID programmeVersionId, UUID funderId, String funderName, String feeType, String description, BigDecimal amount, boolean includesVat,
                            LocalDate validFrom, LocalDate validTo, String sourceNote, String status, String withdrawnReason, boolean inForceToday, Instant createdAt) {
    }

    /** One line offered as a suggestion for an enrolment. general is true for a line that is not for any one funder. */
    public record SuggestionRow(UUID priceLineId, String feeType, String description, BigDecimal amount, boolean includesVat, boolean general, String funderName, String sourceNote) {
    }

    public record Suggestion(UUID enrolmentId, LocalDate on, String note, List<SuggestionRow> rows) {
    }
}
