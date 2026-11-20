package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.LitigationDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Court cases against in-memory stand-ins for the repositories (no database, no Spring): stages, hearings and their calendar entries, orders,
 * concluding and reopening, the event log, tenant isolation and ethical walls. The migration's constraints and triggers were run against PostgreSQL separately.
 */
class LpLitigationServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID anele = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(LpLitigationService.ZONE);

    private final Map<UUID, LpLitigationCase> cases = new LinkedHashMap<>();
    private final Map<UUID, LpHearing> hearings = new LinkedHashMap<>();
    private final Map<UUID, LpCourtOrder> orders = new LinkedHashMap<>();
    private final List<LpLitigationEvent> events = new ArrayList<>();
    private final Map<UUID, LpMatterKeyDate> keyDates = new LinkedHashMap<>();
    private final Map<UUID, LpMatter> matters = new LinkedHashMap<>();
    private final List<UUID> walledMatters = new ArrayList<>(), walledClients = new ArrayList<>();

    private LpLitigationService service;
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
        cases.clear(); hearings.clear(); orders.clear(); events.clear(); keyDates.clear(); matters.clear(); walledMatters.clear(); walledClients.clear();
        var caseRepo = fake(LpLitigationCaseRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(cases.get((UUID) a[1])).filter(c -> c.getTenantId().equals(a[0])),
                "findForMatter", a -> cases.values().stream().filter(c -> c.getTenantId().equals(a[0]) && c.getMatterId().equals(a[1])).findFirst(),
                "save", a -> { LpLitigationCase c = (LpLitigationCase) a[0]; cases.put(c.getId(), c); return c; },
                "findFirmExcluding", a -> {
                    boolean concluded = (Boolean) a[3]; String stage = (String) a[4]; String pat = (String) a[5];
                    String needle = pat.substring(1, pat.length() - 1);
                    return new PageImpl<>(cases.values().stream().filter(c -> c.getTenantId().equals(a[0]) && !((List<?>) a[1]).contains(c.getMatterId()) && !((List<?>) a[2]).contains(c.getClientId())
                            && (concluded || c.isOpen()) && (stage.isEmpty() || c.getStage().equals(stage))
                            && ((c.getCaseNumber() == null ? "" : c.getCaseNumber()).toLowerCase().contains(needle) || c.getCourtName().toLowerCase().contains(needle)
                            || (c.getOpposingParty() == null ? "" : c.getOpposingParty()).toLowerCase().contains(needle))).toList());
                }));
        var hearingRepo = fake(LpHearingRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(hearings.get((UUID) a[1])).filter(h -> h.getTenantId().equals(a[0])),
                "findForCase", a -> hearings.values().stream().filter(h -> h.getTenantId().equals(a[0]) && h.getCaseId().equals(a[1])).sorted(Comparator.comparing(LpHearing::getHearingDate).reversed()).toList(),
                "countScheduled", a -> hearings.values().stream().filter(h -> h.getTenantId().equals(a[0]) && h.getCaseId().equals(a[1]) && h.scheduled()).count(),
                "save", a -> { LpHearing h = (LpHearing) a[0]; hearings.put(h.getId(), h); return h; },
                "findRollExcluding", a -> {
                    List<Object[]> rows = new ArrayList<>();
                    for (LpHearing h : hearings.values()) {
                        LpLitigationCase c = cases.get(h.getCaseId());
                        if (h.getTenantId().equals(a[0]) && h.scheduled() && !h.getHearingDate().isBefore((LocalDate) a[1]) && !h.getHearingDate().isAfter((LocalDate) a[2])
                                && !((List<?>) a[3]).contains(h.getMatterId()) && !((List<?>) a[4]).contains(c.getClientId())) rows.add(new Object[]{h, c});
                    }
                    rows.sort(Comparator.comparing(r -> ((LpHearing) r[0]).getHearingDate()));
                    return rows;
                }));
        var orderRepo = fake(LpCourtOrderRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(orders.get((UUID) a[1])).filter(o -> o.getTenantId().equals(a[0])),
                "findForCase", a -> orders.values().stream().filter(o -> o.getTenantId().equals(a[0]) && o.getCaseId().equals(a[1])).toList(),
                "save", a -> { LpCourtOrder o = (LpCourtOrder) a[0]; orders.put(o.getId(), o); return o; }));
        var eventRepo = fake(LpLitigationEventRepository.class, Map.of(
                "save", a -> { events.add((LpLitigationEvent) a[0]); return a[0]; },
                "findRecentForCase", a -> { List<LpLitigationEvent> l = new ArrayList<>(events.stream().filter(e -> e.getTenantId().equals(a[0]) && e.getCaseId().equals(a[1])).toList()); Collections.reverse(l); return l; }));
        var keyRepo = fake(LpMatterKeyDateRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(keyDates.get((UUID) a[1])).filter(k -> k.getTenantId().equals(a[0])),
                "save", a -> { LpMatterKeyDate k = (LpMatterKeyDate) a[0]; keyDates.put(k.getId(), k); return k; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(matters.get((UUID) a[1])).filter(m -> m.getTenantId().equals(a[0])),
                "findAllById", a -> { List<LpMatter> l = new ArrayList<>(); for (Object id : (Iterable<?>) a[0]) if (matters.containsKey(id)) l.add(matters.get(id)); return l; }));
        LpWallGuard guard = new LpWallGuard() {
            @Override public void requireAccess(TenantId t, LpMatter m) { }
            @Override public void requireMatterAccess(TenantId t, UUID id) { if (walledMatters.contains(id)) throw new AccessDeniedException("walled"); }
            @Override public void requireClientAccess(TenantId t, UUID id) { if (walledClients.contains(id)) throw new AccessDeniedException("walled"); }
            @Override public void requireScopeAccess(TenantId t, UUID m, UUID c) { }
            @Override public Barred barred(TenantId t) { return Barred.of(walledMatters, walledClients); }
        };
        service = new LpLitigationService(caseRepo, hearingRepo, orderRepo, eventRepo, keyRepo, matterRepo, guard);
        matter = newMatter("M-1", "Dlamini v Nkosi", clientId);
    }

    private LpMatter newMatter(String no, String name, UUID client) {
        LpMatter m = LpMatter.create(firm, client, UUID.randomUUID(), no, "LITIGATION", name, null, "HOURLY", null, LocalDate.of(2026, 1, 5), null);
        matters.put(m.getId(), m);
        return m;
    }

    private OpenCaseRequest openReq(UUID matterId, String stage, String number) {
        return new OpenCaseRequest(matterId, "HIGH_COURT", "Gauteng Division, Johannesburg", "PLAINTIFF", number, stage, "Nkosi Trading", "Smith Inc", "smith@example.test", "Judge Mokoena",
                today.minusDays(30), today.minusDays(20), "Urgent");
    }

    private CaseDetail openCase() { return service.open(firm, openReq(matter.getId(), "PLEADINGS", "12345/2026"), anele, "Anele"); }

    private LocalDate weekday(int plusDays) {
        LocalDate d = today.plusDays(plusDays);
        while (!LitigationRules.dateWarnings(d).isEmpty()) d = d.plusDays(1);
        return d;
    }

    private List<String> eventTypes() { return events.stream().map(LpLitigationEvent::getEventType).toList(); }

    private static HearingRequest hearing(LocalDate d) { return new HearingRequest("TRIAL", d, null, "Court 4B", "Adv. Sithole"); }

    // ---- the case ------------------------------------------------------------------------------------------------------

    @Test
    void openingACaseRecordsItAndLogsIt() {
        var c = openCase();
        assertEquals("PLEADINGS", c.stage());
        assertTrue(c.open());
        assertEquals("M-1 Dlamini v Nkosi", c.matterLabel());
        assertEquals("12345/2026", c.caseNumber());
        assertEquals("Anele", c.createdByName());
        assertEquals(List.of("CASE_OPENED"), eventTypes());
        assertEquals(c.id(), service.forMatter(firm, matter.getId()).litigation().id());
    }

    @Test
    void aMatterWithoutACaseReturnsNothing() {
        assertNull(service.forMatter(firm, matter.getId()).litigation());
    }

    @Test
    void aMatterHasOneCase() {
        openCase();
        var e = assertThrows(IllegalStateException.class, () -> openCase());
        assertTrue(e.getMessage().contains("already"));
        assertEquals(1, cases.size());
    }

    @Test
    void aCaseBeyondPreActionNeedsItsNumber() {
        assertThrows(IllegalArgumentException.class, () -> service.open(firm, openReq(matter.getId(), "PLEADINGS", null), anele, "Anele"));
        var c = service.open(firm, openReq(matter.getId(), "PRE_ACTION", null), anele, "Anele");
        assertNull(c.caseNumber());
        var e = assertThrows(IllegalStateException.class, () -> service.changeStage(firm, c.id(), new StageRequest("PLEADINGS", null), anele, "Anele"));
        assertTrue(e.getMessage().contains("case number"));
        service.edit(firm, c.id(), new EditCaseRequest("HIGH_COURT", "Gauteng Division", "PLAINTIFF", "999/2026", null, null, null, null, null, null, null), anele, "Anele");
        assertEquals("PLEADINGS", service.changeStage(firm, c.id(), new StageRequest("PLEADINGS", null), anele, "Anele").stage());
    }

    @Test
    void aCaseCannotBeOpenedAsConcluded() {
        assertThrows(IllegalArgumentException.class, () -> service.open(firm, openReq(matter.getId(), "CONCLUDED", "1/26"), anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.open(firm, openReq(null, "PLEADINGS", "1/26"), anele, "Anele"));
    }

    @Test
    void stageMovesAreLoggedAndGoingBackNeedsAReason() {
        var c = openCase();
        var d = service.changeStage(firm, c.id(), new StageRequest("TRIAL", null), anele, "Anele");
        assertEquals("TRIAL", d.stage());
        assertThrows(IllegalStateException.class, () -> service.changeStage(firm, c.id(), new StageRequest("DISCOVERY", "no"), anele, "Anele"));
        assertEquals("DISCOVERY", service.changeStage(firm, c.id(), new StageRequest("DISCOVERY", "Further discovery was ordered"), anele, "Anele").stage());
        assertEquals(List.of("CASE_OPENED", "STAGE_CHANGED", "STAGE_CHANGED"), eventTypes());
        assertEquals("TRIAL to DISCOVERY: Further discovery was ordered", events.get(2).getDetail());
    }

    @Test
    void editingChangesTheDetailsButNotTheStage() {
        var c = openCase();
        var d = service.edit(firm, c.id(), new EditCaseRequest("HIGH_COURT", "Gauteng Division", "DEFENDANT", "12345/2026", " New Party ", null, null, "Judge Ndlovu", null, null, null), anele, "Anele");
        assertEquals("DEFENDANT", d.ourRole());
        assertEquals("New Party", d.opposingParty());
        assertEquals("PLEADINGS", d.stage());
        assertEquals("DETAILS_EDITED", eventTypes().get(1));
        assertThrows(IllegalArgumentException.class, () -> service.edit(firm, c.id(), new EditCaseRequest("HIGH_COURT", "Gauteng Division", "DEFENDANT", null, null, null, null, null, null, null, null), anele, "Anele"));
    }

    // ---- hearings ------------------------------------------------------------------------------------------------------

    @Test
    void aHearingIsAlsoACourtDateOnTheMatterCalendar() {
        var c = openCase();
        LocalDate date = weekday(10);
        var d = service.scheduleHearing(firm, c.id(), hearing(date), anele, "Anele");
        assertEquals(1, d.hearings().size());
        assertTrue(d.warnings().isEmpty());
        assertEquals(1, keyDates.size());
        LpMatterKeyDate kd = keyDates.values().iterator().next();
        assertEquals("COURT_DATE", kd.getDateType());
        assertEquals(date, kd.getDueDate());
        assertEquals("Trial: 12345/2026", kd.getDescription());
        assertEquals(matter.getId(), kd.getMatterId());
        assertEquals(kd.getId(), d.hearings().get(0).keyDateId());
        assertEquals("HEARING_SET", eventTypes().get(1));
    }

    @Test
    void aWeekendHearingIsAllowedWithAWarning() {
        var c = openCase();
        LocalDate d = today.plusDays(10);
        while (d.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) d = d.plusDays(1);
        var r = service.scheduleHearing(firm, c.id(), hearing(d), anele, "Anele");
        assertEquals(1, r.warnings().size());
        assertTrue(r.warnings().get(0).contains("Saturday"));
    }

    @Test
    void postponingMovesTheSameCalendarEntryAndCountsIt() {
        var c = openCase();
        var s = service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele");
        UUID hid = s.hearings().get(0).id();
        LocalDate newDate = weekday(30);
        var d = service.postponeHearing(firm, hid, new PostponeRequest(newDate, null, "Court roll was full that day"), anele, "Anele");
        assertEquals(1, d.hearings().get(0).postponements());
        assertEquals(newDate, d.hearings().get(0).hearingDate());
        assertEquals(1, keyDates.size());
        assertEquals(newDate, keyDates.values().iterator().next().getDueDate());
        assertEquals("PENDING", keyDates.values().iterator().next().getStatus());
        assertEquals("HEARING_MOVED", eventTypes().get(2));
        assertThrows(IllegalArgumentException.class, () -> service.postponeHearing(firm, hid, new PostponeRequest(weekday(40), null, "no"), anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.postponeHearing(firm, hid, new PostponeRequest(newDate, null, "Same date again for no reason"), anele, "Anele"));
    }

    @Test
    void holdingAHearingNeedsItToHaveHappenedAndAnOutcomeAndClosesTheCalendarEntry() {
        var c = openCase();
        var s = service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele");
        UUID hid = s.hearings().get(0).id();
        assertThrows(IllegalArgumentException.class, () -> service.holdHearing(firm, hid, new HeldRequest("Argued and reserved judgment"), anele, "Anele"));
        // a hearing that is already in the past
        var past = service.scheduleHearing(firm, c.id(), hearing(today.minusDays(2)), anele, "Anele");
        UUID pid = past.hearings().stream().filter(h -> h.hearingDate().equals(today.minusDays(2))).findFirst().orElseThrow().id();
        assertThrows(IllegalArgumentException.class, () -> service.holdHearing(firm, pid, new HeldRequest("short"), anele, "Anele"));
        var d = service.holdHearing(firm, pid, new HeldRequest("Argued and reserved judgment"), anele, "Anele");
        var held = d.hearings().stream().filter(h -> h.id().equals(pid)).findFirst().orElseThrow();
        assertEquals("HELD", held.status());
        assertEquals("COMPLETED", keyDates.get(held.keyDateId()).getStatus());
        assertEquals("PENDING", keyDates.get(s.hearings().get(0).keyDateId()).getStatus());
        assertThrows(IllegalStateException.class, () -> service.holdHearing(firm, pid, new HeldRequest("Argued and reserved judgment"), anele, "Anele"));
    }

    @Test
    void cancellingNeedsAReasonAndClosesTheCalendarEntry() {
        var c = openCase();
        var s = service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele");
        UUID hid = s.hearings().get(0).id();
        assertThrows(IllegalArgumentException.class, () -> service.cancelHearing(firm, hid, new CancelRequest("no"), anele, "Anele"));
        var d = service.cancelHearing(firm, hid, new CancelRequest("The parties settled the dispute"), anele, "Anele");
        assertEquals("CANCELLED", d.hearings().get(0).status());
        assertEquals("COMPLETED", keyDates.get(d.hearings().get(0).keyDateId()).getStatus());
        assertEquals("HEARING_CANCELLED", eventTypes().get(2));
        assertThrows(IllegalStateException.class, () -> service.postponeHearing(firm, hid, new PostponeRequest(weekday(30), null, "Trying to move a cancelled one"), anele, "Anele"));
    }

    // ---- orders --------------------------------------------------------------------------------------------------------

    @Test
    void anOrderWithADateToComplyByIsAFilingDeadline() {
        var c = openCase();
        LocalDate due = weekday(14);
        var d = service.recordOrder(firm, c.id(), new OrderRequest(null, today, "Defendant to deliver the plea", due), anele, "Anele");
        assertEquals(1, d.orders().size());
        assertTrue(d.orders().get(0).outstanding());
        LpMatterKeyDate kd = keyDates.get(d.orders().get(0).keyDateId());
        assertEquals("FILING_DEADLINE", kd.getDateType());
        assertEquals(due, kd.getDueDate());
        assertEquals("ORDER_RECORDED", eventTypes().get(1));
    }

    @Test
    void anOrderWithoutADateAddsNothingToTheCalendar() {
        var c = openCase();
        var d = service.recordOrder(firm, c.id(), new OrderRequest(null, today, "Costs reserved for later", null), anele, "Anele");
        assertTrue(keyDates.isEmpty());
        assertFalse(d.orders().get(0).outstanding());
        assertThrows(IllegalStateException.class, () -> service.markComplied(firm, d.orders().get(0).id(), new ComplyRequest(today, null), anele, "Anele"));
    }

    @Test
    void anOrderCanBeLinkedOnlyToAHearingOnTheSameCase() {
        var c = openCase();
        var other = newMatter("M-2", "Other", UUID.randomUUID());
        var c2 = service.open(firm, openReq(other.getId(), "PLEADINGS", "777/2026"), anele, "Anele");
        var h = service.scheduleHearing(firm, c2.id(), hearing(weekday(10)), anele, "Anele").hearings().get(0);
        assertThrows(IllegalArgumentException.class, () -> service.recordOrder(firm, c.id(), new OrderRequest(h.id(), today, "Defendant to deliver the plea", null), anele, "Anele"));
        assertThrows(ResourceNotFoundException.class, () -> service.recordOrder(firm, c.id(), new OrderRequest(UUID.randomUUID(), today, "Defendant to deliver the plea", null), anele, "Anele"));
    }

    @Test
    void complyingClosesTheDeadlineOnceOnly() {
        var c = openCase();
        var d = service.recordOrder(firm, c.id(), new OrderRequest(null, today.minusDays(3), "Defendant to deliver the plea", weekday(14)), anele, "Anele");
        UUID oid = d.orders().get(0).id();
        var done = service.markComplied(firm, oid, new ComplyRequest(today, " Delivered by hand "), anele, "Anele");
        assertFalse(done.orders().get(0).outstanding());
        assertEquals("Delivered by hand", done.orders().get(0).compliedNote());
        assertEquals("COMPLETED", keyDates.get(done.orders().get(0).keyDateId()).getStatus());
        assertEquals("ORDER_COMPLIED", eventTypes().get(2));
        assertThrows(IllegalStateException.class, () -> service.markComplied(firm, oid, new ComplyRequest(today, null), anele, "Anele"));
    }

    @Test
    void complyingCannotPredateTheOrder() {
        var c = openCase();
        var d = service.recordOrder(firm, c.id(), new OrderRequest(null, today.minusDays(3), "Defendant to deliver the plea", weekday(14)), anele, "Anele");
        assertThrows(IllegalArgumentException.class, () -> service.markComplied(firm, d.orders().get(0).id(), new ComplyRequest(today.minusDays(4), null), anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.markComplied(firm, d.orders().get(0).id(), new ComplyRequest(today.plusDays(1), null), anele, "Anele"));
    }

    @Test
    void anOverdueOrderIsWarnedAbout() {
        var c = openCase();
        service.recordOrder(firm, c.id(), new OrderRequest(null, today.minusDays(30), "Defendant to deliver the plea", today.minusDays(5)), anele, "Anele");
        var d = service.get(firm, c.id());
        assertEquals(1, d.warnings().size());
        assertTrue(d.warnings().get(0).contains("not been marked complied"));
    }

    // ---- concluding ----------------------------------------------------------------------------------------------------

    @Test
    void aCaseCannotBeConcludedWhileAHearingIsStillScheduled() {
        var c = openCase();
        service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele");
        var e = assertThrows(IllegalStateException.class, () -> service.conclude(firm, c.id(), new ConcludeRequest("SETTLED", today, "Settled on the day of trial"), anele, "Anele"));
        assertTrue(e.getMessage().contains("still scheduled"));
        assertEquals("PLEADINGS", service.get(firm, c.id()).stage());
    }

    @Test
    void concludingRecordsTheOutcomeAndWarnsAboutOpenOrders() {
        var c = openCase();
        service.recordOrder(firm, c.id(), new OrderRequest(null, today, "Defendant to deliver the plea", weekday(14)), anele, "Anele");
        var d = service.conclude(firm, c.id(), new ConcludeRequest("SETTLED", today, "Settled on written terms"), anele, "Anele");
        assertEquals("CONCLUDED", d.stage());
        assertFalse(d.open());
        assertEquals("SETTLED", d.outcome());
        assertEquals(today, d.concludedOn());
        assertEquals(1, d.warnings().size());
        assertEquals("CASE_CONCLUDED", eventTypes().get(eventTypes().size() - 1));
    }

    @Test
    void aConcludedCaseIsFrozenUntilReopened() {
        var c = openCase();
        service.conclude(firm, c.id(), new ConcludeRequest("WON", today, "Judgment for the plaintiff"), anele, "Anele");
        assertThrows(IllegalStateException.class, () -> service.changeStage(firm, c.id(), new StageRequest("TRIAL", null), anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.recordOrder(firm, c.id(), new OrderRequest(null, today, "Defendant to deliver the plea", null), anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.edit(firm, c.id(), new EditCaseRequest("HIGH_COURT", "Gauteng", "PLAINTIFF", "1/26", null, null, null, null, null, null, null), anele, "Anele"));
        assertThrows(IllegalStateException.class, () -> service.conclude(firm, c.id(), new ConcludeRequest("WON", today, "Judgment for the plaintiff"), anele, "Anele"));
    }

    @Test
    void reopeningNeedsAReasonAndAStageAndClearsTheOutcome() {
        var c = openCase();
        service.conclude(firm, c.id(), new ConcludeRequest("LOST", today, "Judgment for the defendant"), anele, "Anele");
        assertThrows(IllegalArgumentException.class, () -> service.reopen(firm, c.id(), new ReopenRequest("APPEAL", "no"), anele, "Anele"));
        assertThrows(IllegalArgumentException.class, () -> service.reopen(firm, c.id(), new ReopenRequest("CONCLUDED", "Leave to appeal was granted"), anele, "Anele"));
        var d = service.reopen(firm, c.id(), new ReopenRequest("APPEAL", "Leave to appeal was granted"), anele, "Anele");
        assertEquals("APPEAL", d.stage());
        assertTrue(d.open());
        assertNull(d.outcome());
        assertNull(d.concludedOn());
        assertEquals("CASE_REOPENED", eventTypes().get(eventTypes().size() - 1));
        assertThrows(IllegalStateException.class, () -> service.reopen(firm, c.id(), new ReopenRequest("APPEAL", "Reopening an open case"), anele, "Anele"));
    }

    // ---- lists, tenants and walls --------------------------------------------------------------------------------------

    @Test
    void theFirmListHidesConcludedCasesUnlessAskedAndFiltersByStageAndText() {
        var c = openCase();
        var m2 = newMatter("M-2", "Other", UUID.randomUUID());
        var c2 = service.open(firm, openReq(m2.getId(), "TRIAL", "777/2026"), anele, "Anele");
        service.conclude(firm, c2.id(), new ConcludeRequest("WON", today, "Judgment for the plaintiff"), anele, "Anele");
        assertEquals(1, service.firmList(firm, null, null, false, PageRequest.of(0, 20)).getContent().size());
        assertEquals(2, service.firmList(firm, null, null, true, PageRequest.of(0, 20)).getContent().size());
        assertEquals(1, service.firmList(firm, "777", null, true, PageRequest.of(0, 20)).getContent().size());
        assertEquals(1, service.firmList(firm, null, "PLEADINGS", true, PageRequest.of(0, 20)).getContent().size());
        assertEquals(c.id(), service.firmList(firm, "nkosi", "PLEADINGS", false, PageRequest.of(0, 20)).getContent().get(0).id());
    }

    @Test
    void wildcardsInTheSearchAreTakenAsPlainText() {
        openCase();
        assertEquals(1, service.firmList(firm, "%", null, false, PageRequest.of(0, 20)).getContent().size());
        assertEquals(1, service.firmList(firm, "_", null, false, PageRequest.of(0, 20)).getContent().size());
    }

    @Test
    void theFirmListShowsTheNextHearing() {
        var c = openCase();
        LocalDate next = weekday(10);
        service.scheduleHearing(firm, c.id(), hearing(weekday(30)), anele, "Anele");
        service.scheduleHearing(firm, c.id(), hearing(next), anele, "Anele");
        assertEquals(next, service.firmList(firm, null, null, false, PageRequest.of(0, 20)).getContent().get(0).nextHearing());
    }

    @Test
    void theCourtRollListsScheduledHearingsInRangeInOrder() {
        var c = openCase();
        LocalDate a = weekday(5), b = weekday(12);
        service.scheduleHearing(firm, c.id(), hearing(b), anele, "Anele");
        service.scheduleHearing(firm, c.id(), hearing(a), anele, "Anele");
        var held = service.scheduleHearing(firm, c.id(), hearing(weekday(300)), anele, "Anele");
        var roll = service.courtRoll(firm, today, today.plusDays(60));
        assertEquals(List.of(a, b), roll.stream().map(RollRow::hearingDate).toList());
        assertEquals("M-1 Dlamini v Nkosi", roll.get(0).matterLabel());
        assertEquals("12345/2026", roll.get(0).caseNumber());
        assertEquals(3, service.courtRoll(firm, today, today.plusDays(400)).size());
        assertThrows(IllegalArgumentException.class, () -> service.courtRoll(firm, today, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.courtRoll(firm, today, today.plusDays(401)));
        assertThrows(IllegalArgumentException.class, () -> service.courtRoll(firm, null, today));
    }

    @Test
    void anotherFirmCannotSeeOrChangeACase() {
        var c = openCase();
        var h = service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele").hearings().get(0);
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherFirm, c.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.changeStage(otherFirm, c.id(), new StageRequest("TRIAL", null), anele, "Anele"));
        assertThrows(ResourceNotFoundException.class, () -> service.holdHearing(otherFirm, h.id(), new HeldRequest("Argued and reserved judgment"), anele, "Anele"));
        assertEquals(0, service.firmList(otherFirm, null, null, true, PageRequest.of(0, 20)).getContent().size());
        assertTrue(service.courtRoll(otherFirm, today, today.plusDays(60)).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.forMatter(otherFirm, matter.getId()));
    }

    @Test
    void aWalledMatterIsClosedOffEverywhere() {
        var c = openCase();
        var h = service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele").hearings().get(0);
        var o = service.recordOrder(firm, c.id(), new OrderRequest(null, today, "Defendant to deliver the plea", weekday(14)), anele, "Anele").orders().get(0);
        walledMatters.add(matter.getId());
        assertThrows(AccessDeniedException.class, () -> service.forMatter(firm, matter.getId()));
        assertThrows(AccessDeniedException.class, () -> service.get(firm, c.id()));
        assertThrows(AccessDeniedException.class, () -> service.edit(firm, c.id(), new EditCaseRequest("HIGH_COURT", "Gauteng", "PLAINTIFF", "1/26", null, null, null, null, null, null, null), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.changeStage(firm, c.id(), new StageRequest("TRIAL", null), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.conclude(firm, c.id(), new ConcludeRequest("WON", today, "Judgment for the plaintiff"), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.scheduleHearing(firm, c.id(), hearing(weekday(20)), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.postponeHearing(firm, h.id(), new PostponeRequest(weekday(30), null, "Court roll was full that day"), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.holdHearing(firm, h.id(), new HeldRequest("Argued and reserved judgment"), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.cancelHearing(firm, h.id(), new CancelRequest("The parties settled the dispute"), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.recordOrder(firm, c.id(), new OrderRequest(null, today, "Another order of the court", null), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.markComplied(firm, o.id(), new ComplyRequest(today, null), anele, "Anele"));
        assertThrows(AccessDeniedException.class, () -> service.open(firm, openReq(matter.getId(), "PLEADINGS", "1/26"), anele, "Anele"));
        assertEquals(0, service.firmList(firm, null, null, true, PageRequest.of(0, 20)).getContent().size());
        assertTrue(service.courtRoll(firm, today, today.plusDays(60)).isEmpty());
    }

    @Test
    void aWalledClientIsLeftOutOfTheFirmViews() {
        var c = openCase();
        service.scheduleHearing(firm, c.id(), hearing(weekday(10)), anele, "Anele");
        walledClients.add(clientId);
        assertEquals(0, service.firmList(firm, null, null, true, PageRequest.of(0, 20)).getContent().size());
        assertTrue(service.courtRoll(firm, today, today.plusDays(60)).isEmpty());
    }

    @Test
    void everyChangeIsLoggedWithWhoDidIt() {
        var c = openCase();
        service.changeStage(firm, c.id(), new StageRequest("DISCOVERY", null), anele, "Anele");
        var d = service.get(firm, c.id());
        assertEquals(2, d.events().size());
        assertEquals("STAGE_CHANGED", d.events().get(0).eventType());
        assertEquals("PLEADINGS to DISCOVERY", d.events().get(0).detail());
        assertEquals("Anele", d.events().get(0).actorName());
    }
}
