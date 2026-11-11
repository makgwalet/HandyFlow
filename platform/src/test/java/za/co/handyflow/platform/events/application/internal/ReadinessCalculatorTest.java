package za.co.handyflow.platform.events.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.events.application.internal.ReadinessCalculator.ChecklistRow;
import za.co.handyflow.platform.events.application.internal.ReadinessCalculator.StaffRow;
import za.co.handyflow.platform.events.dto.ReadinessResponse.PhaseProgress;
import za.co.handyflow.platform.events.dto.ReadinessResponse.ReadinessCheck;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessCalculatorTest {

    private static ReadinessCheck check(List<ReadinessCheck> checks, String code) {
        return checks.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    private static List<ReadinessCheck> evaluate(List<String> contacts, List<StaffRow> staff,
                                                 List<ChecklistRow> checklist, long sessions) {
        return ReadinessCalculator.evaluate(contacts, staff, checklist, sessions);
    }

    private static final List<String> GOOD_CONTACTS = List.of("MEDICAL", "VENUE");
    private static final List<StaffRow> GOOD_STAFF = List.of(
            new StaffRow("COORDINATOR", "CONFIRMED"), new StaffRow("STEWARD", "CONFIRMED"));
    private static final List<ChecklistRow> GOOD_CHECKLIST = List.of(
            new ChecklistRow("PRE_EVENT", true, true), new ChecklistRow("SETUP", true, true),
            new ChecklistRow("CONTINGENCY", false, true));

    @Test
    void aBrandNewEventWithNothingPlanned_isNotReady_onEveryCheck() {
        List<ReadinessCheck> checks = evaluate(List.of(), List.of(), List.of(), 0);

        assertThat(checks).hasSize(6);
        assertThat(checks).allMatch(c -> !c.passed());   // empty must read as "not ready", never as "fine"
    }

    @Test
    void anEventWithEverythingInPlace_isReady() {
        List<ReadinessCheck> checks = evaluate(GOOD_CONTACTS, GOOD_STAFF, GOOD_CHECKLIST, 4);
        assertThat(checks).allMatch(ReadinessCheck::passed);
    }

    // ── Emergency contacts ────────────────────────────────────────────────────

    @Test
    void emergencyContacts_needAMedicalContact_andAtLeastTwoContacts() {
        assertThat(check(evaluate(List.of("VENUE", "SECURITY"), GOOD_STAFF, GOOD_CHECKLIST, 1), "EMERGENCY_CONTACTS"))
                .satisfies(c -> {
                    assertThat(c.passed()).isFalse();
                    assertThat(c.detail()).contains("medical");
                });
        assertThat(check(evaluate(List.of("MEDICAL"), GOOD_STAFF, GOOD_CHECKLIST, 1), "EMERGENCY_CONTACTS"))
                .satisfies(c -> {
                    assertThat(c.passed()).isFalse();
                    assertThat(c.detail()).contains("Only one");
                });
    }

    // ── Roster ────────────────────────────────────────────────────────────────

    @Test
    void aCoordinatorIsRequired_andCancelledOrNoShowCoordinatorsDoNotCount() {
        List<StaffRow> noCoordinator = List.of(new StaffRow("STEWARD", "CONFIRMED"));
        assertThat(check(evaluate(GOOD_CONTACTS, noCoordinator, GOOD_CHECKLIST, 1), "COORDINATOR_ASSIGNED").passed()).isFalse();

        List<StaffRow> gone = List.of(new StaffRow("COORDINATOR", "CANCELLED"), new StaffRow("COORDINATOR", "NO_SHOW"));
        assertThat(check(evaluate(GOOD_CONTACTS, gone, GOOD_CHECKLIST, 1), "COORDINATOR_ASSIGNED").passed()).isFalse();
    }

    @Test
    void theRosterMustBeConfirmed_notJustFilledIn() {
        List<StaffRow> staff = List.of(new StaffRow("COORDINATOR", "CONFIRMED"), new StaffRow("STEWARD", "ASSIGNED"));
        ReadinessCheck c = check(evaluate(GOOD_CONTACTS, staff, GOOD_CHECKLIST, 1), "ROSTER_CONFIRMED");

        assertThat(c.passed()).isFalse();
        assertThat(c.detail()).contains("1 of 2");
    }

    @Test
    void cancelledAndNoShowShifts_doNotCountAsUnconfirmed() {
        List<StaffRow> staff = List.of(new StaffRow("COORDINATOR", "CONFIRMED"),
                new StaffRow("STEWARD", "CANCELLED"), new StaffRow("STEWARD", "NO_SHOW"));
        assertThat(check(evaluate(GOOD_CONTACTS, staff, GOOD_CHECKLIST, 1), "ROSTER_CONFIRMED").passed()).isTrue();
    }

    // ── Checklist ─────────────────────────────────────────────────────────────

    @Test
    void anOpenRequiredPreEventOrSetupItem_blocksReadiness() {
        List<ChecklistRow> checklist = List.of(new ChecklistRow("PRE_EVENT", true, false), new ChecklistRow("SETUP", true, true),
                new ChecklistRow("CONTINGENCY", false, true));
        ReadinessCheck c = check(evaluate(GOOD_CONTACTS, GOOD_STAFF, checklist, 1), "REQUIRED_CHECKLIST_DONE");

        assertThat(c.passed()).isFalse();
        assertThat(c.detail()).contains("1 required");
    }

    @Test
    void optionalItems_andLaterPhases_doNotBlockReadiness() {
        List<ChecklistRow> checklist = List.of(
                new ChecklistRow("PRE_EVENT", true, true),
                new ChecklistRow("PRE_EVENT", false, false),     // optional, open
                new ChecklistRow("EVENT_DAY", true, false),      // later phase, open
                new ChecklistRow("POST_EVENT", true, false),     // later phase, open
                new ChecklistRow("CONTINGENCY", false, true));
        assertThat(check(evaluate(GOOD_CONTACTS, GOOD_STAFF, checklist, 1), "REQUIRED_CHECKLIST_DONE").passed()).isTrue();
    }

    @Test
    void noPreEventOrSetupItemsAtAll_isNotReady() {
        List<ChecklistRow> onlyLater = List.of(new ChecklistRow("EVENT_DAY", true, true));
        ReadinessCheck c = check(evaluate(GOOD_CONTACTS, GOOD_STAFF, onlyLater, 1), "REQUIRED_CHECKLIST_DONE");

        assertThat(c.passed()).isFalse();
        assertThat(c.detail()).contains("No pre-event or setup");
    }

    // ── Run sheet and contingency ─────────────────────────────────────────────

    @Test
    void theRunSheetCheck_needsAtLeastOneSession() {
        assertThat(check(evaluate(GOOD_CONTACTS, GOOD_STAFF, GOOD_CHECKLIST, 0), "RUN_SHEET_PRESENT").passed()).isFalse();
        assertThat(check(evaluate(GOOD_CONTACTS, GOOD_STAFF, GOOD_CHECKLIST, 1), "RUN_SHEET_PRESENT").passed()).isTrue();
    }

    @Test
    void contingencyPlans_mustBeReviewed_notJustWritten() {
        List<ChecklistRow> unreviewed = List.of(new ChecklistRow("PRE_EVENT", true, true), new ChecklistRow("SETUP", true, true),
                new ChecklistRow("CONTINGENCY", true, false));
        ReadinessCheck c = check(evaluate(GOOD_CONTACTS, GOOD_STAFF, unreviewed, 1), "CONTINGENCY_PLANS");

        assertThat(c.passed()).isFalse();
        assertThat(c.detail()).contains("none reviewed");
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    @Test
    void progress_listsEveryPhaseInOrder_withCountsAndOpenRequiredItems() {
        List<ChecklistRow> items = List.of(
                new ChecklistRow("SETUP", true, true), new ChecklistRow("SETUP", true, false), new ChecklistRow("SETUP", false, false),
                new ChecklistRow("PRE_EVENT", true, true));

        List<PhaseProgress> progress = ReadinessCalculator.progress(items);

        assertThat(progress).extracting(PhaseProgress::phase)
                .containsExactly("PRE_EVENT", "SETUP", "EVENT_DAY", "BREAKDOWN", "POST_EVENT", "CONTINGENCY");
        PhaseProgress setup = progress.get(1);
        assertThat(setup.total()).isEqualTo(3);
        assertThat(setup.done()).isEqualTo(1);
        assertThat(setup.requiredOpen()).isEqualTo(1);      // the optional open one doesn't count
        assertThat(progress.get(2).total()).isZero();       // an empty phase is still listed
    }
}
