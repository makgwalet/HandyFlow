package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WriteOffResponse(
        UUID accidentId,
        LocalDate assessmentDate,
        String assessorName,
        LocalDate decisionDate,
        String decisionNote,
        BigDecimal preAccidentValue,
        BigDecimal salvageValue,
        String salvageBuyer,
        LocalDate salvageSoldAt,
        BigDecimal insuranceSettlementAmount,
        LocalDate insuranceSettledAt
) {}