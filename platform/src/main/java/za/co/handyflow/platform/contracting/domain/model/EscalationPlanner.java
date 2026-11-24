package za.co.handyflow.platform.contracting.domain.model;

import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Absence;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Stage;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Thresholds;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides, for one waiting approval, whether a reminder or the escalation is due now and who should be told. Pure: it reads nothing and
 * sends nothing, so the same inputs always give the same plan. The service carries the plan out.
 *
 * <p>A plan can only contain people to tell. It never contains a decision: the engine does not approve, reject or use up an approval.
 */
public final class EscalationPlanner {

    private EscalationPlanner() {}

    /** The approval as the planner needs to see it. */
    public record Waiting(UUID requestedBy, UUID assignedTo, int level, Instant requestedAt) {}

    /** Everything around it: the company's rules, who can approve, who is away, and when it is. */
    public record Context(Thresholds thresholds, ApprovalWorkingTime clock, Instant enabledSince, List<UUID> approverPool,
                          List<UUID> escalationContacts, List<UUID> administrators, List<Absence> absences, LocalDate today, Instant now) {}

    /**
     * @param recipients        who to tell (empty when no one at all can be reached)
     * @param fellBackToEscalation a reminder had no reachable approver, so it went to the escalation contacts instead
     * @param tellRequester     the requester is also told (on escalation)
     * @param skippedEarlierSteps more than one step was overdue (the engine was off or down); only this one is sent
     * @param waitedMinutes     how long it has waited on the company's clock, from when escalation started counting
     */
    public record Plan(Stage stage, long waitedMinutes, List<UUID> recipients, boolean fellBackToEscalation, boolean tellRequester,
                       boolean skippedEarlierSteps) {
        public boolean noOneToTell() { return recipients.isEmpty(); }
    }

    /** Minutes it has waited on the company's clock. Time before escalation was switched on does not count, so switching it on never chases old requests. */
    public static long waitedMinutes(Waiting w, ApprovalWorkingTime clock, Instant enabledSince, Instant now) {
        Instant from = enabledSince != null && enabledSince.isAfter(w.requestedAt()) ? enabledSince : w.requestedAt();
        return clock.minutesBetween(from, now);
    }

    /** Cheap first look: is a step due at all? Lets the sweep skip the user lookups for the many approvals that are not. */
    public static Optional<Stage> stageDue(Waiting w, Thresholds t, ApprovalWorkingTime clock, Instant enabledSince, Instant now) {
        return EscalationRules.stageToSend(w.level(), waitedMinutes(w, clock, enabledSince, now), t);
    }

    /** The step to take now and who to tell, or empty when none is due. */
    public static Optional<Plan> plan(Waiting w, Context c) {
        long waited = waitedMinutes(w, c.clock(), c.enabledSince(), c.now());
        Optional<Stage> due = EscalationRules.stageToSend(w.level(), waited, c.thresholds());
        if (due.isEmpty()) return Optional.empty();
        Stage stage = due.get();
        boolean skipped = stage.level() - w.level() > 1;

        if (stage == Stage.ESCALATED) {
            List<UUID> to = EscalationRules.routeEscalation(c.escalationContacts(), c.administrators(), w.requestedBy(), c.absences(), c.today());
            return Optional.of(new Plan(stage, waited, to, false, true, skipped));
        }
        EscalationRules.Routing r = EscalationRules.routeReminder(w.assignedTo(), c.approverPool(), w.requestedBy(), c.absences(), c.today());
        if (!r.fallbackToEscalationContacts()) return Optional.of(new Plan(stage, waited, r.people(), false, false, skipped));
        List<UUID> fallback = EscalationRules.routeEscalation(c.escalationContacts(), c.administrators(), w.requestedBy(), c.absences(), c.today());
        return Optional.of(new Plan(stage, waited, fallback, true, false, skipped));
    }
}
