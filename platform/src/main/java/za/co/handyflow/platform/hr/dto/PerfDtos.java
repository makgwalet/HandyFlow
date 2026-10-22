package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Performance: what the screens read and what they send. */
public final class PerfDtos {

    private PerfDtos() {}

    public record EventView(String action, String note, String doneBy, Instant doneAt) {}

    /** The signed-in person's place in the reporting lines. */
    public record Me(UUID employeeId, String name, int directReports, boolean canRead, boolean canManage) {}

    // ── Cycles ───────────────────────────────────────────────────────────────────────────────────────
    public record CycleRow(UUID id, String name, LocalDate periodStart, LocalDate periodEnd, LocalDate selfDue, LocalDate managerDue, boolean include360,
                           String status, int total, int awaitingSelf, int awaitingManager, int submitted, int released, int excluded) {}

    public record CycleDetail(CycleRow row, List<ReviewRow> reviews, int withoutReviewer, List<EventView> events, List<String> actions) {}

    public record CycleRequest(String name, LocalDate periodStart, LocalDate periodEnd, LocalDate selfDue, LocalDate managerDue, boolean include360) {}

    public record AddReviewRequest(UUID employeeId) {}

    // ── Reviews ──────────────────────────────────────────────────────────────────────────────────────
    /** A line in a list. The rating is the final rating, and is empty for the employee until the result is released. */
    public record ReviewRow(UUID id, UUID cycleId, String cycleName, UUID employeeId, String employeeName, String jobTitle, UUID reviewerId, String reviewerName,
                            String status, LocalDate selfDue, LocalDate managerDue, Integer rating, Instant sharedAt, Instant ackAt) {}

    /** A request for feedback. The name is shown to the reviewer and HR; what was written, only to HR. */
    public record FeedbackView(UUID id, String relationship, String status, String providerName, Instant requestedAt, Instant respondedAt,
                               Integer rating, String strengths, String improvements) {}

    public record SummaryView(boolean shown, int replies, Double average, List<String> strengths, List<String> improvements, int asked, int declined, int minReplies) {}

    public record ReviewDetail(ReviewRow row, boolean include360, LocalDate periodStart, LocalDate periodEnd,
                               Integer selfRating, String selfComment, Instant selfAt,
                               Integer managerRating, String managerComment, Instant managerAt,
                               Integer finalRating, boolean calibrated, String calibrationReason,
                               String employeeComment, String excludedReason,
                               SummaryView feedback, List<FeedbackView> requests, List<GoalView> goals,
                               List<EventView> events, List<String> actions, List<String> ratingLabels) {}

    public record SelfRequest(Integer rating, String comment) {}
    public record ManagerRequest(Integer rating, String comment) {}
    public record CalibrateRequest(Integer rating, String reason) {}
    public record ReviewerRequest(UUID reviewerId, String reason) {}
    public record ReasonRequest(String reason) {}
    public record AcknowledgeRequest(String comment) {}

    // ── 360 feedback ─────────────────────────────────────────────────────────────────────────────────
    public record FeedbackRequestBody(List<UUID> providerIds, String relationship) {}
    public record FeedbackSubmit(Integer rating, String strengths, String improvements) {}

    /** A request for the signed-in person to give feedback. It says who it is about and what the cycle is, never who else was asked. */
    public record FeedbackTask(UUID id, String aboutName, String jobTitle, String cycleName, String relationship, String status, LocalDate dueDate, Instant requestedAt) {}

    // ── Goals ────────────────────────────────────────────────────────────────────────────────────────
    public record GoalView(UUID id, UUID employeeId, String employeeName, UUID cycleId, String cycleName, String title, String description, String measure,
                           LocalDate dueDate, String status, int progress, boolean overdue, String outcomeNote, String setByName,
                           List<EventView> history, List<String> actions) {}

    public record GoalRequest(UUID employeeId, UUID cycleId, String title, String description, String measure, LocalDate dueDate) {}
    public record GoalEditRequest(String title, String description, String measure, LocalDate dueDate) {}
    public record ProgressRequest(Integer progress, String note) {}
    public record GoalCloseRequest(String outcome, String note) {}

    // ── Improvement plans ────────────────────────────────────────────────────────────────────────────
    public record CheckinView(UUID id, LocalDate onDate, String progress, String note, String doneBy, Instant doneAt) {}

    public record PlanRow(UUID id, UUID employeeId, String employeeName, UUID ownerId, String ownerName, UUID caseId, String caseNumber, String title, String status,
                          LocalDate startDate, LocalDate endDate, LocalDate communicatedOn, boolean shorterThanMinimum, String lastProgress, boolean acknowledged) {}

    public record PlanDetail(PlanRow row, String shortfall, String standard, String support, LocalDate closedOn, String outcomeNote, Instant ackAt, String employeeComment,
                             int minReviewDays, List<CheckinView> checkins, List<EventView> events, List<String> actions) {}

    public record PlanRequest(UUID employeeId, UUID ownerId, UUID caseId, String title, String shortfall, String standard, String support, LocalDate startDate, LocalDate endDate) {}
    public record StartPlanRequest(LocalDate communicatedOn) {}
    public record CheckinRequest(LocalDate onDate, String progress, String note) {}
    public record ExtendRequest(LocalDate endDate, String reason) {}
    public record ClosePlanRequest(String outcome, String note) {}
    public record CaseLinkRequest(UUID caseId) {}
}
