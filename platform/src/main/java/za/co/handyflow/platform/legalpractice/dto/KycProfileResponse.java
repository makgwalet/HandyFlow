package za.co.handyflow.platform.legalpractice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record KycProfileResponse(
        UUID id, UUID clientId, String clientName, String subjectKind, String status,
        String riskGeography, String riskService, String riskClient, String riskChannel, String riskRating, String dueDiligenceLevel, int reviewMonths,
        String pepStatus, LocalDate pepCheckedOn, String pepNote, LocalDate sanctionsCheckedOn, String sanctionsResult,
        String businessNature, String sourceOfFunds, String sourceOfWealth,
        String startedByName, Instant startedAt, UUID submittedBy, String submittedByName, Instant submittedAt, String returnReason,
        String approvedByName, Instant approvedAt, String approvalNote, boolean selfApproved, boolean seniorApproved, LocalDate validUntil,
        boolean needsSenior, List<String> missing, List<Party> parties, List<Item> items) {

    public record Party(UUID id, String role, String name, String idNumber, BigDecimal percent, String method, String pep, LocalDate verifiedOn, String verifiedByName, String note) { }

    public record Item(UUID id, String kind, UUID partyId, String description, String documentRef, UUID evidenceId, LocalDate verifiedOn, String verifiedByName) { }
}
