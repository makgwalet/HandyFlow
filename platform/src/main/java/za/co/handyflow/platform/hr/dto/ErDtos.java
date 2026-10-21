package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Employee relations cases: what the screens read and what they send. */
public final class ErDtos {

    private ErDtos() {}

    // ── Reading ───────────────────────────────────────────────────────────────────────────────────

    /** One line of the case list. */
    public record Row(UUID id, String number, String type, String category, String title, String status, UUID employeeId, String employeeName,
                      boolean confidential, boolean legacy, LocalDate reportedOn, String outcome, LocalDate dueOn, String dueLabel,
                      boolean overdue) {}

    public record Party(UUID id, String name) {}

    public record Charge(UUID id, int seq, String charge, String finding, String findingNote) {}

    public record StepView(String code, String label, LocalDate doneOn, String note) {}

    public record EventView(String action, String note, boolean visibleToEmployee, String doneBy, Instant doneAt) {}

    public record WarningView(UUID caseId, String number, String sanction, LocalDate issuedOn, LocalDate validUntil) {}

    public record Hearing(Instant noticeIssuedAt, LocalDate date, LocalTime time, String venue, LocalDate heldOn, Boolean employeeAttended,
                          String representative, String notes) {}

    public record Decision(String outcome, LocalDate decidedOn, String note, Integer warningMonths, LocalDate warningValidUntil,
                           String employeeResponse, LocalDate communicatedOn, LocalDate dismissalRightsExplainedOn, LocalDate ccmaDeadline,
                           boolean acknowledged, Instant acknowledgedAt) {}

    public record Appeal(LocalDate deadline, LocalDate lodgedOn, String grounds, Party decider, LocalDate decidedOn, String outcome, String note) {}

    public record Suspension(LocalDate from, LocalDate to, String reason, boolean active) {}

    public record Review(LocalDate from, LocalDate to, boolean shorterThanMinimum) {}

    /** Advice for whoever decides: the employee's warnings in force and, for a grievance, whether the response is late. */
    public record Advice(List<WarningView> activeWarnings, List<WarningView> unknownValidity, String guidance, LocalDate responseDue, boolean overdue) {}

    public record PolicyView(int noticeHours, int verbalMonths, int writtenMonths, int finalMonths, int appealDays, int grievanceResponseDays,
                             int minReviewDays) {}

    public record Detail(UUID id, String number, String type, String category, String title, String description, String status,
                         boolean confidential, boolean legacy, Party employee, String jobTitle, String department,
                         LocalDate incidentDate, LocalDate reportedOn, Party owner, Party initiator, Party chair, Party respondent,
                         Hearing hearing, Decision decision, Appeal appeal, Suspension suspension, Review review,
                         LocalDate closedOn, String closedReason,
                         List<Charge> charges, List<StepView> steps, List<EventView> events,
                         Advice advice, List<String> actions, PolicyView policy,
                         List<String> outcomes, List<String> missingSteps) {}

    /** What the employee sees of a case about them. Investigation notes and the case officer's working are left out. */
    public record Mine(UUID id, String number, String type, String category, String title, String description, String status,
                       LocalDate reportedOn, Hearing hearing, List<Charge> charges, Decision decision, Appeal appeal,
                       boolean canAcknowledge, boolean canAppeal, List<EventView> events) {}

    // ── Sending ───────────────────────────────────────────────────────────────────────────────────

    public record OpenRequest(UUID employeeId, String type, String category, String title, String description, LocalDate incidentDate,
                              UUID initiatorId, UUID respondentId, Boolean confidential) {}

    public record LodgeGrievanceRequest(String category, String description, LocalDate incidentDate, UUID respondentId) {}

    public record NoticeRequest(LocalDate hearingDate, LocalTime hearingTime, String venue, UUID chairId, UUID initiatorId, List<String> charges) {}

    public record HearingRequest(LocalDate heldOn, Boolean employeeAttended, String representative, String notes) {}

    public record ChargeFinding(UUID chargeId, String finding, String note) {}

    public record DecisionRequest(List<ChargeFinding> findings, String sanction, Integer warningMonths, String reasons, LocalDate decidedOn) {}

    public record InformalRequest(String sanction, String employeeResponse, String reasons, Integer warningMonths, LocalDate decidedOn) {}

    public record GrievanceDecisionRequest(UUID deciderId, String outcome, String note, LocalDate decidedOn) {}

    public record IncapacityDecisionRequest(UUID deciderId, String outcome, String note, LocalDate decidedOn) {}

    public record CommunicateRequest(LocalDate on, Boolean rightsExplained) {}

    public record AppealRequest(LocalDate lodgedOn, String grounds, Boolean acceptLate) {}

    public record AppealDecisionRequest(UUID deciderId, LocalDate decidedOn, String outcome, String newOutcome, Integer warningMonths, String note) {}

    public record SuspendRequest(LocalDate from, LocalDate to, String reason) {}

    public record DateRequest(LocalDate on) {}

    public record ReasonRequest(String reason) {}

    public record AssignRequest(UUID ownerUserId) {}

    public record PartiesRequest(UUID initiatorId, UUID chairId, UUID respondentId) {}

    public record FlagRequest(boolean confidential) {}

    public record StepRequest(String code, LocalDate doneOn, String note, Boolean undo) {}

    public record ReviewRequest(LocalDate from, LocalDate to) {}

    public record PolicyRequest(int noticeHours, int verbalMonths, int writtenMonths, int finalMonths, int appealDays, int grievanceResponseDays,
                                int minReviewDays) {}
}
