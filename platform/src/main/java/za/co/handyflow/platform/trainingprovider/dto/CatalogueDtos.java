package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CatalogueDtos {

    private CatalogueDtos() {
    }

    // qualifications

    public record QualificationRequest(@NotBlank String title, @NotBlank String qualificationType, String officialRef,
                                       String saqaId, String subFramework, Integer nqfLevel, Integer credits,
                                       UUID authorityId) {
    }

    public record QualificationView(UUID id, String title, String qualificationType, String officialRef, String saqaId,
                                    String subFramework, Integer nqfLevel, Integer credits, UUID authorityId,
                                    String status, String retiredReason, Integer currentVersionNo, int versions) {
    }

    public record QualificationVersionRequest(@NotBlank String registrationStatus, LocalDate registrationStart,
                                              LocalDate registrationEnd, String entryRequirements, String exitOutcomes,
                                              String rulesOfCombination, String sourceNote, LocalDate effectiveFrom) {
    }

    public record QualificationVersionView(UUID id, UUID qualificationId, Integer versionNo, String status,
                                           String registrationStatus, LocalDate registrationStart,
                                           LocalDate registrationEnd, String entryRequirements, String exitOutcomes,
                                           String rulesOfCombination, String sourceNote, LocalDate effectiveFrom,
                                           Instant madeCurrentAt) {
    }

    public record QualificationDetail(QualificationView qualification, List<QualificationVersionView> versions) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    // programmes

    public record ProgrammeRequest(@NotBlank String code, @NotBlank String title, @NotBlank String programmeType,
                                   @NotBlank String trainingActivity, @NotBlank String deliveryMode, UUID qualificationId,
                                   UUID courseId, String description) {
    }

    public record ProgrammeView(UUID id, String code, String title, String programmeType, String trainingActivity,
                                String deliveryMode, UUID qualificationId, String qualificationTitle, UUID courseId,
                                String description, String status, String retiredReason, Integer approvedVersionNo,
                                long liveOfferings, boolean advertisableAsAccredited) {
    }

    public record ProgrammeVersionRequest(UUID qualificationVersionId, BigDecimal learningHours, Integer credits,
                                          String summary, LocalDate effectiveFrom) {
    }

    public record ModuleRequest(@NotBlank String title, @NotBlank String moduleType, Integer credits,
                                BigDecimal notionalHours, Boolean required, String learningOutcomes) {
    }

    public record ModuleView(UUID id, Integer seq, String title, String moduleType, Integer credits,
                             BigDecimal notionalHours, boolean required, String learningOutcomes) {
    }

    public record ProgrammeVersionView(UUID id, Integer versionNo, String status, UUID qualificationVersionId,
                                       BigDecimal learningHours, Integer credits, String summary, LocalDate effectiveFrom,
                                       Instant approvedAt, UUID approvedBy, String approvalOverrideReason,
                                       String creditsNote, List<ModuleView> modules) {
    }

    public record ApproveVersionRequest(String overrideReason) {
    }

    public record OfferingRequest(@NotNull UUID siteId, LocalDate offeredFrom) {
    }

    public record OfferingView(UUID id, UUID programmeId, UUID siteId, String siteName, String status,
                               LocalDate offeredFrom, String coverageNote, boolean covered, String coverageOutcome,
                               String coverageMessage, String withdrawnReason) {
    }

    public record ProgrammeDetail(ProgrammeView programme, List<ProgrammeVersionView> versions, List<OfferingView> offerings) {
    }
}
