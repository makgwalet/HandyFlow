package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ObligationDtos {

    private ObligationDtos() {}

    public record ConditionInput(@NotBlank @Size(max = 20) String field, @NotBlank @Size(max = 10) String operator, @Size(max = 500) String value) {}

    public record EvidenceInput(@NotBlank @Size(max = 120) String documentType, boolean mandatory) {}

    public record SaveVersionRequest(
            @Size(max = 255) String name, @Size(max = 4000) String description, @Size(max = 500) String legalSource,
            @Size(max = 200) String regulator, @Size(max = 40) String jurisdiction, @Size(max = 12) String frequency,
            @Size(max = 20) String dueRuleType, Integer dueMonth, Integer dueDay, Integer dueOffsetDays, Integer dueOffsetMonths,
            Integer validityMonths, @Size(max = 10) String riskLevel, @Size(max = 20) String responsibleRole,
            @Size(max = 4000) String guidance, @Size(max = 2000) String changeSummary,
            @Valid List<ConditionInput> conditions, @Valid List<EvidenceInput> evidence) {}

    public record CreateObligationRequest(@NotBlank @Size(max = 60) String code, @Valid SaveVersionRequest version) {}

    public record ApproveRequest(LocalDate effectiveFrom, @Size(max = 1000) String exceptionReason, @Size(max = 2000) String note) {}

    public record RejectRequest(@Size(max = 2000) String reason) {}

    public record RetireRequest(LocalDate on) {}

    public record ConditionView(String field, String operator, String value) {}

    public record EvidenceView(String documentType, boolean mandatory) {}

    public record VersionView(UUID id, int versionNumber, String reviewStatus, String name, String description, String legalSource,
                              String regulator, String jurisdiction, String frequency, String dueRuleType, Integer dueMonth,
                              Integer dueDay, Integer dueOffsetDays, Integer dueOffsetMonths, Integer validityMonths, String riskLevel,
                              String responsibleRole, String guidance, String changeSummary, UUID authoredBy, UUID submittedBy,
                              Instant submittedAt, UUID reviewedBy, Instant reviewedAt, String reviewNote, String reviewExceptionReason,
                              LocalDate effectiveFrom, LocalDate effectiveTo, boolean inForce,
                              List<ConditionView> conditions, List<EvidenceView> evidence) {}

    public record ObligationSummary(UUID id, String code, String name, Integer inForceVersion, int latestVersion, String latestStatus,
                                    String riskLevel, String frequency, String regulator) {}

    public record ObligationDetail(UUID id, String code, List<VersionView> versions) {}
}
