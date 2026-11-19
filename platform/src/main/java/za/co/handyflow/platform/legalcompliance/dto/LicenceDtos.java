package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Licences, permits and certificates. ReasonRequest / FindingNoteRequest / EvidenceRef come from AssuranceDtos. */
public final class LicenceDtos {

    private LicenceDtos() {}

    public record LicenceRequest(@NotNull LicenceType licenceType, @NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String issuingAuthority,
                                 @Size(max = 100) String licenceNumber, String scope, LocalDate issueDate, LocalDate expiryDate, @NotBlank @Size(max = 255) String ownerName,
                                 int renewalLeadDays, UUID obligationId, String notes) {}

    public record ConditionRequest(@NotBlank String description, LocalDate dueDate, Integer frequencyMonths) {}

    public record MetRequest(@NotNull LocalDate metOn, String note) {}

    public record RenewalSubmitRequest(@NotNull LocalDate submittedOn, @Size(max = 100) String reference) {}

    public record RenewedRequest(LocalDate newIssueDate, @NotNull LocalDate newExpiryDate, @Size(max = 100) String newLicenceNumber) {}

    public record ConditionResponse(UUID id, String description, LocalDate dueDate, Integer frequencyMonths, ConditionStatus status, LocalDate lastMetOn, String note, boolean overdue, boolean dueSoon) {}

    public record LicenceResponse(UUID id, String reference, LicenceType licenceType, String title, String issuingAuthority, String licenceNumber, String scope, LocalDate issueDate,
                                  LocalDate expiryDate, LicenceStatus status, String displayStatus, String ownerName, int renewalLeadDays, UUID obligationId, String obligationTitle,
                                  LocalDate renewalStartedOn, LocalDate renewalSubmittedOn, String renewalReference, List<AssuranceDtos.EvidenceRef> renewalProof, int liveRenewalFiles,
                                  List<Map<String, Object>> renewalHistory, String statusReason, String notes, boolean expired, boolean expiringSoon, Long daysToExpiry,
                                  int conditionCount, int conditionsOverdue, int conditionsBreached) {}

    public record LicenceDetail(LicenceResponse licence, List<ConditionResponse> conditions) {}

    public record LicenceSummary(long live, long valid, long expiring, long expired, long renewalInProgress, long suspended, long conditionsOverdue, long conditionsBreached) {}
}
