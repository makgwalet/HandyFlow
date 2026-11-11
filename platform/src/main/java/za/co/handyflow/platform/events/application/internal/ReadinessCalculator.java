package za.co.handyflow.platform.events.application.internal;

import za.co.handyflow.platform.events.domain.model.EventChecklistItem;
import za.co.handyflow.platform.events.dto.ReadinessResponse.PhaseProgress;
import za.co.handyflow.platform.events.dto.ReadinessResponse.ReadinessCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * "Are we ready to run this event?" as a pure function of the rows that answer it. Each check is a
 * concrete thing a person can go and fix, and each says exactly what is missing — a vague
 * "not ready" teaches people to ignore it.
 * <p>
 * An EMPTY area counts as NOT ready (no contacts, no staff, no checklist). The alternative — treating
 * "nothing recorded" as fine — would make a brand-new event with nothing planned look ready, which is
 * precisely the case the check exists to catch.
 */
final class ReadinessCalculator {

    record StaffRow(String role, String status) {}

    record ChecklistRow(String phase, boolean required, boolean done) {}

    private ReadinessCalculator() {}

    static List<ReadinessCheck> evaluate(List<String> contactTypes, List<StaffRow> staff,
                                         List<ChecklistRow> checklist, long activeSessions) {
        List<ReadinessCheck> checks = new ArrayList<>();

        // 1. Somebody to call, including a medical contact.
        boolean hasMedical = contactTypes.contains("MEDICAL");
        boolean enough = hasMedical && contactTypes.size() >= 2;
        checks.add(new ReadinessCheck("EMERGENCY_CONTACTS",
                "Emergency contacts recorded, including a medical contact", enough,
                contactTypes.isEmpty() ? "No emergency contacts recorded"
                        : !hasMedical ? "No medical contact recorded"
                        : contactTypes.size() < 2 ? "Only one contact recorded — add the venue or security as well"
                        : contactTypes.size() + " contacts recorded"));

        // 2. Someone in charge.
        List<StaffRow> live = staff.stream().filter(s -> isLive(s.status())).toList();
        boolean coordinator = live.stream().anyMatch(s -> "COORDINATOR".equals(s.role()));
        checks.add(new ReadinessCheck("COORDINATOR_ASSIGNED", "An event-day coordinator is rostered", coordinator,
                coordinator ? "Coordinator rostered" : "No coordinator on the roster"));

        // 3. The roster is actually confirmed, not just filled in.
        long unconfirmed = live.stream().filter(s -> "ASSIGNED".equals(s.status())).count();
        boolean rosterOk = !live.isEmpty() && unconfirmed == 0;
        checks.add(new ReadinessCheck("ROSTER_CONFIRMED", "Everyone on the roster has confirmed", rosterOk,
                live.isEmpty() ? "Nobody is rostered"
                        : unconfirmed == 0 ? live.size() + " rostered, all confirmed"
                        : unconfirmed + " of " + live.size() + " still unconfirmed"));

        // 4. The checklist before the doors open.
        List<ChecklistRow> beforeDoors = checklist.stream()
                .filter(c -> "PRE_EVENT".equals(c.phase()) || "SETUP".equals(c.phase())).toList();
        long openRequired = beforeDoors.stream().filter(c -> c.required() && !c.done()).count();
        boolean checklistOk = !beforeDoors.isEmpty() && openRequired == 0;
        checks.add(new ReadinessCheck("REQUIRED_CHECKLIST_DONE",
                "Every required pre-event and setup item is done", checklistOk,
                beforeDoors.isEmpty() ? "No pre-event or setup checklist items recorded"
                        : openRequired == 0 ? "All required items done"
                        : openRequired + " required item(s) still open"));

        // 5. There is a programme.
        checks.add(new ReadinessCheck("RUN_SHEET_PRESENT", "A run sheet has been drawn up", activeSessions > 0,
                activeSessions > 0 ? activeSessions + " session(s) scheduled" : "No sessions scheduled"));

        // 6. Someone has thought about what could go wrong.
        List<ChecklistRow> plans = checklist.stream().filter(c -> "CONTINGENCY".equals(c.phase())).toList();
        long reviewed = plans.stream().filter(ChecklistRow::done).count();
        checks.add(new ReadinessCheck("CONTINGENCY_PLANS", "At least one contingency plan has been reviewed", reviewed > 0,
                plans.isEmpty() ? "No contingency plans recorded"
                        : reviewed > 0 ? reviewed + " of " + plans.size() + " plan(s) reviewed"
                        : plans.size() + " plan(s) recorded, none reviewed yet"));

        return checks;
    }

    /** Progress per phase, in the order an event moves through them. Phases with no items are still listed (as 0). */
    static List<PhaseProgress> progress(List<ChecklistRow> items) {
        List<PhaseProgress> out = new ArrayList<>();
        for (String phase : EventChecklistItem.PHASES) {
            List<ChecklistRow> inPhase = items.stream().filter(i -> phase.equals(i.phase())).toList();
            out.add(new PhaseProgress(phase, inPhase.size(),
                    (int) inPhase.stream().filter(ChecklistRow::done).count(),
                    (int) inPhase.stream().filter(i -> i.required() && !i.done()).count()));
        }
        return out;
    }

    private static boolean isLive(String status) {
        return "ASSIGNED".equals(status) || "CONFIRMED".equals(status) || "ARRIVED".equals(status);
    }
}