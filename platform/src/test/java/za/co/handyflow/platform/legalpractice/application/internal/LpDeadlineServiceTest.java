package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deadline rules, the court calendar and computed deadlines against in-memory stand-ins for the repositories (no database,
 * no Spring). The counting itself is covered by {@code DeadlineCalculatorTest}; the migration was run against PostgreSQL.
 */
class LpDeadlineServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID user = UUID.randomUUID();

    private final Map<UUID, LpDeadlineRule> rules = new LinkedHashMap<>();
    private final List<LpCourtCalendarEntry> calendar = new ArrayList<>();
    private final List<LpDeadlineComputation> computations = new ArrayList<>();
    private final Map<UUID, LpMatterKeyDate> keyDates = new LinkedHashMap<>();
    private final Map<UUID, LpMatter> matters = new LinkedHashMap<>();

    private LpDeadlineService service;
    private LpMatter matter;

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> handlers) {
        InvocationHandler h = (proxy, m, args) -> {
            Function<Object[], Object> f = handlers.get(m.getName());
            if (f != null) return f.apply(args == null ? new Object[0] : args);
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            throw new UnsupportedOperationException(m.getName());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, h);
    }

    @BeforeEach
    void setUp() {
        rules.clear(); calendar.clear(); computations.clear(); keyDates.clear(); matters.clear();
        var ruleRepo = fake(LpDeadlineRuleRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(rules.get((UUID) a[1])).filter(r -> r.getTenantId().equals(a[0])),
                "findAllForTenant", a -> rules.values().stream().filter(r -> r.getTenantId().equals(a[0])).toList(),
                "findByCode", a -> rules.values().stream().filter(r -> r.getTenantId().equals(a[0]) && r.getCode().equalsIgnoreCase((String) a[1])).findFirst(),
                "save", a -> { LpDeadlineRule r = (LpDeadlineRule) a[0]; rules.put(r.getId(), r); return r; }));
        var calendarRepo = fake(LpCourtCalendarEntryRepository.class, Map.of(
                "findActiveById", a -> calendar.stream().filter(e -> e.getTenantId().equals(a[0]) && e.getId().equals(a[1])).findFirst(),
                "findInRange", a -> calendar.stream().filter(e -> e.getTenantId().equals(a[0]) && !e.getEntryDate().isBefore((LocalDate) a[1]) && !e.getEntryDate().isAfter((LocalDate) a[2])).toList(),
                "findOnDate", a -> calendar.stream().filter(e -> e.getTenantId().equals(a[0]) && e.getEntryDate().equals(a[1]) && e.getKind().equals(a[2])).findFirst(),
                "save", a -> { calendar.add((LpCourtCalendarEntry) a[0]); return a[0]; },
                "delete", a -> { calendar.remove(a[0]); return null; }));
        var computationRepo = fake(LpDeadlineComputationRepository.class, Map.of(
                "findActiveById", a -> computations.stream().filter(c -> c.getTenantId().equals(a[0]) && c.getId().equals(a[1])).findFirst(),
                "findAllForMatter", a -> { List<LpDeadlineComputation> mine = new ArrayList<>(); for (var c : computations) if (c.getTenantId().equals(a[0]) && c.getMatterId().equals(a[1])) mine.add(0, c); return mine; },
                "countSuccessors", a -> computations.stream().filter(c -> c.getTenantId().equals(a[0]) && a[1].equals(c.getSupersedesId())).count(),
                "save", a -> { computations.add((LpDeadlineComputation) a[0]); return a[0]; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(matters.get((UUID) a[1])).filter(m -> m.getTenantId().equals(a[0]))));
        var keyDateRepo = fake(LpMatterKeyDateRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(keyDates.get((UUID) a[1])).filter(k -> k.getTenantId().equals(a[0])),
                "findAllForMatter", a -> keyDates.values().stream().filter(k -> k.getTenantId().equals(a[0]) && k.getMatterId().equals(a[1])).toList(),
                "save", a -> { LpMatterKeyDate k = (LpMatterKeyDate) a[0]; keyDates.put(k.getId(), k); return k; }));
        service = new LpDeadlineService(ruleRepo, calendarRepo, computationRepo, matterRepo, keyDateRepo, LpWallGuard.NONE);
        matter = LpMatter.create(firm, UUID.randomUUID(), UUID.randomUUID(), "M-1", "LITIGATION", "Dlamini v Nkosi", null, "HOURLY", (BigDecimal) null, LocalDate.of(2026, 1, 5), null);
        matters.put(matter.getId(), matter);
    }

    private static DeadlineRuleRequest ruleReq(String code, int amount, String unit, String rollover, String recessStart, String recessEnd) {
        return new DeadlineRuleRequest(code, "Rule " + code.toUpperCase(), null, "HIGH_COURT", "Date served", "FILING_DEADLINE", amount, unit, "AFTER", rollover, recessStart, recessEnd, null);
    }

    private DeadlineRuleResponse tenCourtDays() { return service.createRule(firm, ruleReq("hc_10", 10, "COURT_DAYS", "NONE", null, null)); }

    private DeadlineComputationResponse computeOn(DeadlineRuleResponse r, LocalDate trigger) {
        return service.compute(firm, matter.getId(), new ComputeDeadlineRequest(r.id(), trigger, null, null), user, "Jane");
    }

    // ── rules ────────────────────────────────────────────────────────────────

    @Test
    void aRuleIsStoredWithAnUpperCaseCodeAndRejectsBadValues() {
        var r = tenCourtDays();
        assertEquals("HC_10", r.code());
        assertTrue(r.active());
        assertThrows(IllegalArgumentException.class, () -> service.createRule(firm, ruleReq("A1", 10, "FORTNIGHTS", "NONE", null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.createRule(firm, ruleReq("A2", 0, "COURT_DAYS", "NONE", null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.createRule(firm, ruleReq("A3", 5, "COURT_DAYS", "NONE", "12-16", null)));
        assertThrows(IllegalArgumentException.class, () -> service.createRule(firm, ruleReq("A4", 5, "COURT_DAYS", "NONE", "13-40", "01-15")));
        assertThrows(IllegalArgumentException.class, () -> service.createRule(firm, ruleReq("A5", 5, "COURT_DAYS", "WHENEVER", null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.createRule(firm, new DeadlineRuleRequest("A6", "n", null, null, "t", "BIRTHDAY", 5, "COURT_DAYS", "AFTER", "NONE", null, null, null)));
        assertEquals(1, rules.size(), "nothing was stored for the bad ones");
    }

    @Test
    void aCodeIsUniquePerFirmIgnoringCase() {
        tenCourtDays();
        assertThrows(IllegalStateException.class, () -> service.createRule(firm, ruleReq("HC_10", 5, "COURT_DAYS", "NONE", null, null)));
        assertNotNull(service.createRule(otherFirm, ruleReq("HC_10", 5, "COURT_DAYS", "NONE", null, null)), "another firm may use the same code");
    }

    @Test
    void updatingARuleKeepsItsOwnCodeButNotAnotherRulesCode() {
        var a = tenCourtDays();
        var b = service.createRule(firm, ruleReq("OTHER", 5, "COURT_DAYS", "NONE", null, null));
        assertEquals(15, service.updateRule(firm, a.id(), ruleReq("HC_10", 15, "COURT_DAYS", "NONE", null, null)).amount());
        assertThrows(IllegalStateException.class, () -> service.updateRule(firm, b.id(), ruleReq("hc_10", 5, "COURT_DAYS", "NONE", null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.updateRule(otherFirm, a.id(), ruleReq("HC_10", 5, "COURT_DAYS", "NONE", null, null)));
    }

    @Test
    void switchingARuleOffHidesItAndStopsNewComputations() {
        var r = tenCourtDays();
        service.setRuleActive(firm, r.id(), false);
        assertTrue(service.listRules(firm, false).isEmpty());
        assertEquals(1, service.listRules(firm, true).size());
        assertThrows(IllegalStateException.class, () -> computeOn(r, LocalDate.of(2026, 3, 6)));
        service.setRuleActive(firm, r.id(), true);
        assertNotNull(computeOn(r, LocalDate.of(2026, 3, 6)));
    }

    @Test
    void everyTemplateIsAValidRuleAndInstallingTwiceAddsNothingNew() {
        var first = service.installTemplates(firm);
        assertEquals(DeadlineRuleTemplates.ALL.size(), first.added());
        assertEquals(0, first.alreadyThere());
        var again = service.installTemplates(firm);
        assertEquals(0, again.added());
        assertEquals(DeadlineRuleTemplates.ALL.size(), again.alreadyThere());
        assertEquals(DeadlineRuleTemplates.ALL.size(), service.listRules(firm, true).size());
        assertEquals(0, service.listRules(otherFirm, true).size());
    }

    @Test
    void installingNeverOverwritesARuleTheFirmEdited() {
        service.installTemplates(firm);
        var plea = service.listRules(firm, true).stream().filter(r -> r.code().equals("HC_PLEA")).findFirst().orElseThrow();
        service.updateRule(firm, plea.id(), ruleReq("HC_PLEA", 25, "COURT_DAYS", "NONE", null, null));
        service.installTemplates(firm);
        assertEquals(25, service.listRules(firm, true).stream().filter(r -> r.code().equals("HC_PLEA")).findFirst().orElseThrow().amount());
    }

    // ── calendar ─────────────────────────────────────────────────────────────

    @Test
    void theCalendarShowsStatutoryHolidaysAndTheFirmsOwnDays() {
        var base = service.calendar(firm, 2026);
        assertEquals(13, base.size(), "12 holidays plus the observed Monday for Sunday 9 August");
        assertTrue(base.stream().allMatch(d -> d.source().equals("STATUTORY") && d.id() == null));
        service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(2026, 11, 4), "PUBLIC_HOLIDAY", "Local elections"), user, "Jane");
        var with = service.calendar(firm, 2026);
        assertEquals(14, with.size());
        assertEquals(1L, with.stream().filter(d -> d.source().equals("FIRM")).count());
        assertEquals(13, service.calendar(otherFirm, 2026).size(), "another firm's days are not shown");
        assertThrows(IllegalArgumentException.class, () -> service.calendar(firm, 1900));
    }

    @Test
    void aDayCannotBeAddedTwiceOrOutsideTheSupportedYears() {
        var req = new CalendarEntryRequest(LocalDate.of(2026, 11, 4), "COURT_CLOSURE", "Flood");
        service.addCalendarEntry(firm, req, user, "Jane");
        assertThrows(IllegalStateException.class, () -> service.addCalendarEntry(firm, req, user, "Jane"));
        assertThrows(IllegalArgumentException.class, () -> service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(1999, 1, 1), "COURT_CLOSURE", "Old"), user, "Jane"));
        assertThrows(IllegalArgumentException.class, () -> service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(2026, 1, 1), "HOLIDAYISH", "Nope"), user, "Jane"));
        assertThrows(IllegalArgumentException.class, () -> service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(2026, 1, 2), "COURT_CLOSURE", "ab"), user, "Jane"));
    }

    @Test
    void aFirmEntryCanOnlyBeRemovedByItsOwnFirm() {
        var added = service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(2026, 11, 4), "COURT_CLOSURE", "Flood"), user, "Jane");
        assertThrows(ResourceNotFoundException.class, () -> service.removeCalendarEntry(otherFirm, added.id()));
        service.removeCalendarEntry(firm, added.id());
        assertTrue(calendar.isEmpty());
    }

    // ── computing ────────────────────────────────────────────────────────────

    @Test
    void computingCreatesAPendingKeyDateAndAnAuditRow() {
        var r = tenCourtDays();
        var c = computeOn(r, LocalDate.of(2026, 3, 6));
        assertEquals(LocalDate.of(2026, 3, 20), c.dueDate());
        assertTrue(c.current());
        assertFalse(c.drifted());
        assertEquals("Jane", c.computedByName());
        LpMatterKeyDate k = keyDates.get(c.keyDateId());
        assertEquals(LocalDate.of(2026, 3, 20), k.getDueDate());
        assertEquals("FILING_DEADLINE", k.getDateType());
        assertEquals("PENDING", k.getStatus());
        assertEquals("Rule HC_10", k.getDescription(), "defaults to the rule's name");
        assertTrue(k.getNotes().startsWith("Computed: Rule HC_10 (Date served: 2026-03-06)"), k.getNotes());
        assertEquals(1, computations.size());
        assertEquals("HC_10", computations.get(0).getRuleCode());
        assertEquals(10, computations.get(0).getAmount());
    }

    @Test
    void aFirmClosureMovesTheDateAndIsExplained() {
        var r = tenCourtDays();
        service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(2026, 3, 11), "COURT_CLOSURE", "Court flooded"), user, "Jane");
        var c = computeOn(r, LocalDate.of(2026, 3, 6));
        assertEquals(LocalDate.of(2026, 3, 23), c.dueDate());
        assertTrue(c.explanation().contains("Wed 11 Mar 2026: court closed (Court flooded)"), c.explanation().toString());
    }

    @Test
    void anotherFirmsClosureDoesNotAffectThisFirm() {
        var r = tenCourtDays();
        service.addCalendarEntry(otherFirm, new CalendarEntryRequest(LocalDate.of(2026, 3, 11), "COURT_CLOSURE", "Flood"), user, "Jane");
        assertEquals(LocalDate.of(2026, 3, 20), computeOn(r, LocalDate.of(2026, 3, 6)).dueDate());
    }

    @Test
    void aProclaimedHolidayCountsAsAHolidayForTheSundayRule() {
        var r = service.createRule(firm, ruleReq("CAL_30", 30, "CALENDAR_DAYS", "SKIP_SUNDAY_AND_HOLIDAYS", null, null));
        assertEquals(LocalDate.of(2026, 3, 12), computeOn(r, LocalDate.of(2026, 2, 10)).dueDate());
    }

    @Test
    void aProclaimedHolidayOnTheLastDayMovesIt() {
        var r = service.createRule(firm, ruleReq("CAL_30", 30, "CALENDAR_DAYS", "SKIP_SUNDAY_AND_HOLIDAYS", null, null));
        service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(2026, 3, 12), "PUBLIC_HOLIDAY", "Proclaimed day"), user, "Jane");
        var c = computeOn(r, LocalDate.of(2026, 2, 10));
        assertEquals(LocalDate.of(2026, 3, 13), c.dueDate());
        assertTrue(c.explanation().get(0).contains("Proclaimed day"), c.explanation().toString());
    }

    @Test
    void aClosureDoesNotMoveASundayAndHolidayRollover() {
        var r = service.createRule(firm, ruleReq("CAL_30", 30, "CALENDAR_DAYS", "SKIP_SUNDAY_AND_HOLIDAYS", null, null));
        service.addCalendarEntry(firm, new CalendarEntryRequest(LocalDate.of(2026, 3, 12), "COURT_CLOSURE", "Flood"), user, "Jane");
        assertEquals(LocalDate.of(2026, 3, 12), computeOn(r, LocalDate.of(2026, 2, 10)).dueDate());
    }

    @Test
    void previewSavesNothing() {
        var r = tenCourtDays();
        var p = service.preview(firm, new DeadlinePreviewRequest(r.id(), LocalDate.of(2026, 3, 6)));
        assertEquals(LocalDate.of(2026, 3, 20), p.dueDate());
        assertEquals("Friday", p.dueDayOfWeek());
        assertTrue(keyDates.isEmpty());
        assertTrue(computations.isEmpty());
    }

    @Test
    void anotherFirmsMatterOrRuleIsNotFound() {
        var r = tenCourtDays();
        assertThrows(ResourceNotFoundException.class, () -> service.compute(otherFirm, matter.getId(), new ComputeDeadlineRequest(r.id(), LocalDate.of(2026, 3, 6), null, null), user, "X"));
        var theirRule = service.createRule(otherFirm, ruleReq("THEIRS", 5, "COURT_DAYS", "NONE", null, null));
        assertThrows(ResourceNotFoundException.class, () -> computeOn(theirRule, LocalDate.of(2026, 3, 6)));
        assertThrows(ResourceNotFoundException.class, () -> service.compute(firm, UUID.randomUUID(), new ComputeDeadlineRequest(r.id(), LocalDate.of(2026, 3, 6), null, null), user, "X"));
        assertTrue(keyDates.isEmpty());
    }

    @Test
    void theSameDeadlineIsNotComputedTwiceWhileItIsStillPending() {
        var r = tenCourtDays();
        var first = computeOn(r, LocalDate.of(2026, 3, 6));
        assertThrows(IllegalStateException.class, () -> computeOn(r, LocalDate.of(2026, 3, 6)));
        assertEquals(1, keyDates.size());
        assertNotNull(computeOn(r, LocalDate.of(2026, 3, 9)), "a different trigger date is a different deadline");
        keyDates.get(first.keyDateId()).complete();
        assertNotNull(computeOn(r, LocalDate.of(2026, 3, 6)), "once completed, the same inputs may be computed again");
    }

    @Test
    void aTriggerDateOutsideTheSupportedYearsIsRefused() {
        var r = tenCourtDays();
        assertThrows(IllegalArgumentException.class, () -> computeOn(r, LocalDate.of(1999, 12, 31)));
        assertThrows(IllegalArgumentException.class, () -> computeOn(r, LocalDate.of(2101, 1, 1)));
    }

    @Test
    void aDescriptionAndNotesFromThePersonAreKept() {
        var r = tenCourtDays();
        var c = service.compute(firm, matter.getId(), new ComputeDeadlineRequest(r.id(), LocalDate.of(2026, 3, 6), "File the plea", "Counsel briefed"), user, "Jane");
        LpMatterKeyDate k = keyDates.get(c.keyDateId());
        assertEquals("File the plea", k.getDescription());
        assertTrue(k.getNotes().endsWith("\nCounsel briefed"), k.getNotes());
    }

    // ── correcting ───────────────────────────────────────────────────────────

    @Test
    void correctingMovesTheKeyDateKeepsTheOldRowAndNeedsAReason() {
        var r = tenCourtDays();
        var c = computeOn(r, LocalDate.of(2026, 3, 6));
        keyDates.get(c.keyDateId()).acknowledge();
        assertThrows(IllegalArgumentException.class, () -> service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "typo", false), user, "Jane"));
        assertEquals(LocalDate.of(2026, 3, 20), keyDates.get(c.keyDateId()).getDueDate(), "nothing moved without a reason");

        var fixed = service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", false), user, "Jane");
        assertEquals(LocalDate.of(2026, 3, 23), fixed.dueDate());
        assertEquals(c.id(), fixed.supersedesId());
        assertEquals(c.keyDateId(), fixed.keyDateId(), "the same key date moves; no second one is created");
        assertEquals(1, keyDates.size());
        LpMatterKeyDate k = keyDates.get(c.keyDateId());
        assertEquals(LocalDate.of(2026, 3, 23), k.getDueDate());
        assertFalse(k.isAcknowledged(), "a moved date has to be noticed again");
        assertEquals(2, computations.size());

        var history = service.listForMatter(firm, matter.getId());
        assertEquals(2, history.size());
        assertEquals(fixed.id(), history.get(0).id(), "newest first");
        assertTrue(history.get(0).current());
        assertFalse(history.get(1).current());
        assertEquals("Served on the 9th, not the 6th", history.get(0).reason());
    }

    @Test
    void anOldComputationCannotBeCorrectedAgain() {
        var r = tenCourtDays();
        var c = computeOn(r, LocalDate.of(2026, 3, 6));
        service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", false), user, "Jane");
        assertThrows(IllegalStateException.class, () -> service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 10), "Another correction here", false), user, "Jane"));
        assertEquals(2, computations.size());
    }

    @Test
    void aCompletedOrMissedKeyDateCannotBeMoved() {
        var r = tenCourtDays();
        var c = computeOn(r, LocalDate.of(2026, 3, 6));
        keyDates.get(c.keyDateId()).complete();
        assertThrows(IllegalStateException.class, () -> service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", false), user, "Jane"));
        assertEquals(LocalDate.of(2026, 3, 20), keyDates.get(c.keyDateId()).getDueDate());
    }

    @Test
    void anotherFirmCannotCorrectAComputation() {
        var c = computeOn(tenCourtDays(), LocalDate.of(2026, 3, 6));
        assertThrows(ResourceNotFoundException.class, () -> service.recompute(otherFirm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", false), user, "X"));
        assertTrue(service.listForMatter(otherFirm, matter.getId()).isEmpty());
    }

    @Test
    void aCorrectionKeepsTheRuleAsItWasUnlessToldToUseTheCurrentOne() {
        var r = tenCourtDays();
        var c = computeOn(r, LocalDate.of(2026, 3, 6));
        service.updateRule(firm, r.id(), ruleReq("HC_10", 5, "COURT_DAYS", "NONE", null, null));
        var old = service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", false), user, "Jane");
        assertEquals(10, old.amount(), "the rule as it was when the date was first worked out");
        assertEquals(LocalDate.of(2026, 3, 23), old.dueDate());
        var current = service.recompute(firm, old.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "The rule was corrected to five days", true), user, "Jane");
        assertEquals(5, current.amount());
        assertEquals(LocalDate.of(2026, 3, 16), current.dueDate());
    }

    @Test
    void usingTheCurrentRuleFailsIfItIsSwitchedOff() {
        var r = tenCourtDays();
        var c = computeOn(r, LocalDate.of(2026, 3, 6));
        service.setRuleActive(firm, r.id(), false);
        assertThrows(IllegalStateException.class, () -> service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", true), user, "Jane"));
        assertNotNull(service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", false), user, "Jane"));
    }

    @Test
    void aHandEditedKeyDateIsFlaggedAsDriftedAndTheNoteIsNotDuplicated() {
        var r = tenCourtDays();
        var c = service.compute(firm, matter.getId(), new ComputeDeadlineRequest(r.id(), LocalDate.of(2026, 3, 6), null, "Counsel briefed"), user, "Jane");
        keyDates.get(c.keyDateId()).update("FILING_DEADLINE", LocalDate.of(2026, 3, 25), "Rule HC_10", keyDates.get(c.keyDateId()).getNotes());
        var drifted = service.listForMatter(firm, matter.getId()).get(0);
        assertTrue(drifted.drifted());
        assertEquals(LocalDate.of(2026, 3, 25), drifted.keyDateDueDate());
        assertEquals(LocalDate.of(2026, 3, 20), drifted.dueDate(), "what the rule said is kept");

        service.recompute(firm, c.id(), new RecomputeDeadlineRequest(LocalDate.of(2026, 3, 9), "Served on the 9th, not the 6th", false), user, "Jane");
        String notes = keyDates.get(c.keyDateId()).getNotes();
        assertEquals(1, notes.split("Computed:", -1).length - 1, notes);
        assertTrue(notes.endsWith("Counsel briefed"), notes);
        assertFalse(service.listForMatter(firm, matter.getId()).get(0).drifted());
    }
}
