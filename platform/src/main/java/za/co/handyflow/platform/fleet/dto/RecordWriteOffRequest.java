package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Declare a vehicle a write-off (POST) or update its salvage / settlement details (PUT).
 * {@code accidentId} links the accident case and is honoured on POST only.
 */
public record RecordWriteOffRequest(
        UUID accidentId,
        LocalDate assessmentDate,
        @Size(max = 255) String assessorName,
        @NotNull LocalDate decisionDate,
        String decisionNote,
        @PositiveOrZero BigDecimal preAccidentValue,       // defaults to the vehicle's estimated / book value
        @PositiveOrZero BigDecimal salvageValue,
        @Size(max = 255) String salvageBuyer,
        LocalDate salvageSoldAt,
        @PositiveOrZero BigDecimal insuranceSettlementAmount,
        LocalDate insuranceSettledAt                       // defaults to today when a settlement amount is given
) {}