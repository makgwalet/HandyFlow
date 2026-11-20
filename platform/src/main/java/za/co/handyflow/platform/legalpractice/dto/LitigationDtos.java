package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class LitigationDtos {

    private LitigationDtos() { }

    public record CaseSummary(UUID id, UUID matterId, String matterLabel, UUID clientId, String courtLevel, String courtName, String caseNumber, String ourRole,
                              String stage, String opposingParty, LocalDate nextHearing, String outcome, Instant updatedAt) { }

    public record HearingResponse(UUID id, String hearingType, LocalDate hearingDate, LocalTime hearingTime, String courtRoom, String appearing, String status,
                                  int postponements, String outcome, String cancelReason, UUID keyDateId) { }

    public record OrderResponse(UUID id, UUID hearingId, LocalDate orderDate, String summary, LocalDate complianceDue, LocalDate compliedOn, String compliedNote,
                                boolean outstanding, UUID keyDateId, String createdByName) { }

    public record EventResponse(String eventType, String actorName, String detail, Instant occurredAt) { }

    public record CaseDetail(UUID id, UUID matterId, String matterLabel, UUID clientId, String courtLevel, String courtName, String caseNumber, String ourRole, String stage,
                             boolean open, String opposingParty, String opposingAttorney, String opposingAttorneyContact, String judge, LocalDate issuedOn, LocalDate servedOn,
                             String outcome, LocalDate concludedOn, String outcomeNote, String notes, String createdByName, Instant createdAt, Instant updatedAt,
                             List<HearingResponse> hearings, List<OrderResponse> orders, List<EventResponse> events, List<String> warnings) { }

    /** The matter screen asks for its case; there may be none yet. */
    public record MatterLitigation(CaseDetail litigation) { }

    public record RollRow(UUID hearingId, UUID caseId, UUID matterId, String matterLabel, String courtName, String caseNumber, String hearingType, LocalDate hearingDate,
                          LocalTime hearingTime, String courtRoom, String appearing, int postponements, List<String> warnings) { }

    public record OpenCaseRequest(UUID matterId, String courtLevel, String courtName, String ourRole, String caseNumber, String stage, String opposingParty, String opposingAttorney,
                                  String opposingAttorneyContact, String judge, LocalDate issuedOn, LocalDate servedOn, String notes) { }

    public record EditCaseRequest(String courtLevel, String courtName, String ourRole, String caseNumber, String opposingParty, String opposingAttorney,
                                  String opposingAttorneyContact, String judge, LocalDate issuedOn, LocalDate servedOn, String notes) { }

    public record StageRequest(String stage, String note) { }

    public record ConcludeRequest(String outcome, LocalDate concludedOn, String note) { }

    public record ReopenRequest(String stage, String reason) { }

    public record HearingRequest(String hearingType, LocalDate hearingDate, LocalTime hearingTime, String courtRoom, String appearing) { }

    public record PostponeRequest(LocalDate hearingDate, LocalTime hearingTime, String reason) { }

    public record HeldRequest(String outcome) { }

    public record CancelRequest(String reason) { }

    public record OrderRequest(UUID hearingId, LocalDate orderDate, String summary, LocalDate complianceDue) { }

    public record ComplyRequest(LocalDate compliedOn, String note) { }
}
