package za.co.handyflow.platform.legalcompliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.legalcompliance.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Business continuity. All "overdue" and "met" fields are worked out on read. */
public final class ContinuityDtos {

    private ContinuityDtos() {}

    public record ProcessRequest(@NotBlank @Size(max = 255) String name, String description, @NotBlank @Size(max = 255) String ownerName, @Size(max = 100) String department,
                                 @NotNull Criticality criticality, int rtoHours, int rpoHours, String dependencies, int biaReviewMonths) {}

    public record BiaReviewRequest(@NotNull LocalDate reviewedOn) {}

    public record PlanRequest(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 20) String versionLabel, @NotBlank @Size(max = 255) String ownerName, @NotBlank String content, int reviewMonths) {}

    public record TestRequest(@NotNull LocalDate testDate, @NotNull BcTestType testType, @NotNull BcTestOutcome outcome, BigDecimal achievedRtoHours, String issues, String actions,
                              @NotBlank @Size(max = 255) String conductedBy) {}

    public record ProcessResponse(UUID id, String reference, String name, String description, String ownerName, String department, Criticality criticality, int rtoHours, int rpoHours,
                                  String dependencies, BcProcessStatus status, LocalDate biaReviewedOn, int biaReviewMonths, boolean biaOverdue,
                                  UUID approvedPlanId, String approvedPlanReference, String approvedPlanVersion, LocalDate planReviewDue, boolean planReviewOverdue,
                                  LocalDate lastTestDate, BcTestOutcome lastTestOutcome, Boolean lastTestRtoMet, LocalDate testDueOn, boolean testOverdue, String retiredReason) {}

    public record PlanResponse(UUID id, String reference, UUID processId, String processName, String title, String versionLabel, String ownerName, String content, BcPlanStatus status,
                               int reviewMonths, String authorName, String approvedByName, LocalDate approvedOn, LocalDate nextReviewDue, LocalDate lastReviewedOn, boolean reviewOverdue, String retiredReason) {}

    public record TestResponse(UUID id, UUID planId, String planReference, LocalDate testDate, BcTestType testType, BcTestOutcome outcome, int rtoTargetHours, BigDecimal achievedRtoHours,
                               Boolean rtoMet, String issues, String actions, String conductedByName, String recordedByName) {}

    public record ProcessDetail(ProcessResponse process, List<PlanResponse> plans, List<TestResponse> tests) {}

    public record ContinuitySummary(long processes, long critical, long withoutApprovedPlan, long criticalWithoutPlan, long biaOverdue, long planReviewOverdue, long testOverdue, long lastTestUnsuccessful) {}
}
