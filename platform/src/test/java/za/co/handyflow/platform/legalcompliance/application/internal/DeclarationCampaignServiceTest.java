package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.DeclarationCampaignDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos.DeclarationRequest;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos.DeclarationResponse;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The campaign flow end to end, with in-memory stand-ins for the repositories, the ethics service and the notification module. */
class DeclarationCampaignServiceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    private static final UUID MANAGER = UUID.randomUUID(), ANNA = UUID.randomUUID(), BEN = UUID.randomUUID(), CARA = UUID.randomUUID();

    private final List<DeclarationCampaign> campaignStore = new ArrayList<>();
    private final List<DeclarationReturn> returnStore = new ArrayList<>();
    private final List<CoiDeclaration> coiStore = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    private final List<List<Recipient>> sentTo = new ArrayList<>();
    private List<UserDirectory.Person> people;
    private final List<String> askedDepartments = new ArrayList<>();
    private List<Recipient> ownerList;
    private boolean ownersFail;
    private boolean directoryFails;
    private DeclarationCampaignService service;
    private long ref;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> {
            Object r = handler.apply(m.getName(), args == null ? new Object[0] : args);
            if (r != null) return r;
            if (m.getReturnType() == List.class) return List.of();
            if (m.getReturnType() == Optional.class) return Optional.empty();
            return null;
        });
    }

    @BeforeEach
    void setUp() {
        people = List.of(new UserDirectory.Person(ANNA, "Anna", "anna@x.co.za"), new UserDirectory.Person(BEN, "Ben", "ben@x.co.za"), new UserDirectory.Person(CARA, "Cara", "cara@x.co.za"));
        DeclarationCampaignRepository campaigns = proxy(DeclarationCampaignRepository.class, (n, a) -> switch (n) {
            case "findForTenant" -> campaignStore.stream().filter(c -> c.getId().equals(a[1])).findFirst();
            case "search" -> campaignStore;
            case "nextRef" -> ++ref;
            case "save" -> { if (!campaignStore.contains(a[0])) campaignStore.add((DeclarationCampaign) a[0]); yield a[0]; }
            default -> null;
        });
        DeclarationReturnRepository returns = proxy(DeclarationReturnRepository.class, (n, a) -> switch (n) {
            case "findForTenant" -> returnStore.stream().filter(r -> r.getId().equals(a[1])).findFirst();
            case "findByCampaign" -> returnStore.stream().filter(r -> r.getCampaignId().equals(a[1])).toList();
            case "findForUser" -> returnStore.stream().filter(r -> r.getUserId().equals(a[1])).toList();
            case "findPending" -> returnStore.stream().filter(r -> r.getCampaignId().equals(a[1]) && r.isPending()).toList();
            case "saveAll" -> { for (Object o : (Iterable<?>) a[0]) returnStore.add((DeclarationReturn) o); yield a[0]; }
            case "save" -> { if (!returnStore.contains(a[0])) returnStore.add((DeclarationReturn) a[0]); yield a[0]; }
            default -> null;
        });
        CoiDeclarationRepository declarations = proxy(CoiDeclarationRepository.class, (n, a) -> switch (n) {
            case "search" -> coiStore.stream().filter(d -> a[1] == null || d.getDeclarantId().equals(a[1])).toList();
            case "findForTenant" -> coiStore.stream().filter(d -> d.getId().equals(a[1])).findFirst();
            default -> null;
        });
        DeclarationMaker maker = (t, req, uid, uname) -> {
            CoiDeclaration d = CoiDeclaration.declare(t, "COI-" + (coiStore.size() + 1), uid, uname, req.declarationType(), req.counterparty(), req.description(), req.estimatedValue(), TODAY, 12);
            coiStore.add(d);
            return new DeclarationResponse(d.getId(), d.getReference(), uid, uname, d.getDeclarationType(), d.getCounterparty(), d.getDescription(), d.getEstimatedValue(), d.getDeclaredOn(),
                    d.getStatus(), null, null, null, null, 12, null, false, true);
        };
        DeclarationNotifier notifier = (t, type, title, message, to) -> { sent.add(type + "|" + title); sentTo.add(to); };
        RegulatoryAuditService trail = new RegulatoryAuditService(null) {
            @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID a, String n) { audit.add(e + "|" + ev + "|" + d); }
        };
        ownerList = List.of(Recipient.user(MANAGER, "Mgr", "mgr@x.co.za", null));
        ownersFail = false;
        directoryFails = false;
        CampaignOwners owners = (t, by) -> { if (ownersFail) throw new IllegalStateException("identity down"); assertEquals(MANAGER, by); return ownerList; };
        service = new DeclarationCampaignService(campaigns, returns, declarations, maker, (t, dept) -> { if (directoryFails) throw new IllegalStateException("db down"); askedDepartments.add(String.valueOf(dept)); return people; }, notifier, owners, trail);
    }

    private CampaignResponse launch() { return service.launch(T, new CampaignRequest("Annual declaration 2026", "Declare all interests", TODAY.plusDays(14), null), MANAGER, "Mgr"); }

    private UUID returnOf(UUID user) { return returnStore.stream().filter(r -> r.getUserId().equals(user)).findFirst().orElseThrow().getId(); }

    private static DeclarationRequest interest() { return new DeclarationRequest(DeclarationType.DIRECTORSHIP, "Acme Supplies (Pty) Ltd", "I am a director of a company that supplies our business", new BigDecimal("1000"), 12); }

    private void existingCleared(UUID user, DeclarationStatus outcome) {
        CoiDeclaration d = CoiDeclaration.declare(T, "COI-OLD" + coiStore.size(), user, "X", DeclarationType.OUTSIDE_EMPLOYMENT, "Old employer", "Part-time consulting for a former employer", null, TODAY.minusDays(200), 12);
        if (outcome != DeclarationStatus.DECLARED) d.decide(outcome, "Reviewed and acceptable in the circumstances", outcome == DeclarationStatus.MITIGATED ? "Recuse from related decisions" : null, UUID.randomUUID(), "Rev", TODAY.minusDays(190));
        coiStore.add(d);
    }

    @Test @DisplayName("Launching creates one pending return per person, tells them, and records it")
    void launches() {
        CampaignResponse c = launch();
        assertEquals("DEC-00001", c.reference());
        assertEquals(3, c.total());
        assertEquals(3, c.pending());
        assertEquals(0, c.percentComplete());
        assertEquals(3, returnStore.size());
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).startsWith(NotificationType.LEGALCOMPLIANCE_DECLARATION_REQUEST + "|"));
        assertEquals(3, sentTo.get(0).size());
        assertTrue(audit.get(0).startsWith("DECLARATION_CAMPAIGN|LAUNCHED|DEC-00001 to 3 user(s)"));
    }

    @Test @DisplayName("A campaign with nobody to ask, or a due date in the past, is refused and nothing is created")
    void launchRefused() {
        people = List.of();
        HandyFlowException e = assertThrows(HandyFlowException.class, this::launch);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        people = List.of(new UserDirectory.Person(ANNA, "Anna", null));
        assertThrows(HandyFlowException.class, () -> service.launch(T, new CampaignRequest("Late", null, TODAY.minusDays(1), null), MANAGER, "Mgr"));
        assertEquals(0, campaignStore.size());
        assertEquals(0, returnStore.size());
    }

    @Test @DisplayName("Saying 'nothing to declare' records the statement and moves the campaign on")
    void nil() {
        CampaignResponse c = launch();
        MyReturn r = service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna");
        assertEquals(ReturnStatus.NIL, r.status());
        assertEquals(DeclarationReturn.NIL_STATEMENT, r.statement());
        CampaignResponse after = service.detail(T, c.id()).campaign();
        assertEquals(1, after.nil());
        assertEquals(2, after.pending());
        assertEquals(33, after.percentComplete());
        assertTrue(audit.stream().anyMatch(l -> l.startsWith("DECLARATION_RETURN|NIL|")));
    }

    @Test @DisplayName("Nobody can answer for someone else, and someone else's return looks like it does not exist")
    void ownReturnsOnly() {
        launch();
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class, () -> service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), BEN, "Ben"));
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class, () -> service.declare(T, returnOf(ANNA), interest(), BEN, "Ben"));
        assertEquals(ReturnStatus.PENDING, returnStore.stream().filter(r -> r.getUserId().equals(ANNA)).findFirst().orElseThrow().getStatus());
        assertEquals(1, service.mine(T, BEN).size());
        assertEquals("Ben", returnStore.stream().filter(r -> r.getUserId().equals(BEN)).findFirst().orElseThrow().getUserName());
    }

    @Test @DisplayName("'Unchanged' needs a live earlier declaration; withdrawn and refused ones do not count")
    void unchanged() {
        launch();
        assertThrows(HandyFlowException.class, () -> service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna"));
        existingCleared(ANNA, DeclarationStatus.NOT_PERMITTED);
        assertThrows(HandyFlowException.class, () -> service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna"));
        existingCleared(ANNA, DeclarationStatus.CLEARED);
        MyReturn r = service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna");
        assertEquals(ReturnStatus.UNCHANGED, r.status());
        assertEquals(1, r.existingDeclarations());
        existingCleared(BEN, DeclarationStatus.MITIGATED);
        assertEquals(ReturnStatus.UNCHANGED, service.answer(T, returnOf(BEN), new AnswerRequest(Answer.UNCHANGED), BEN, "Ben").status());
        assertEquals(0, service.mine(T, CARA).get(0).existingDeclarations(), "one person's declarations are never counted for another");
    }

    @Test @DisplayName("Declaring creates an ordinary declaration for the person and links it to their return")
    void declares() {
        launch();
        MyReturn r = service.declare(T, returnOf(ANNA), interest(), ANNA, "Anna");
        assertEquals(ReturnStatus.DECLARED, r.status());
        assertEquals(1, r.declarations().size());
        assertEquals("Acme Supplies (Pty) Ltd", r.declarations().get(0).counterparty());
        assertEquals(1, coiStore.size());
        assertEquals(ANNA, coiStore.get(0).getDeclarantId());
        assertEquals(DeclarationStatus.DECLARED, r.declarations().get(0).status());
        assertEquals(0, r.existingDeclarations(), "what was declared through the campaign is not an 'earlier' declaration");
        service.declare(T, returnOf(ANNA), interest(), ANNA, "Anna");
        assertEquals(2, service.mine(T, ANNA).get(0).declarations().size());
        assertEquals(1, service.detail(T, campaignStore.get(0).getId()).campaign().declared());
    }

    @Test @DisplayName("After declaring, the person cannot say they have nothing to declare")
    void noBackpedalling() {
        launch();
        service.declare(T, returnOf(ANNA), interest(), ANNA, "Anna");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test @DisplayName("A closed campaign takes no more answers or declarations, and closing needs a reason")
    void closing() {
        CampaignResponse c = launch();
        assertThrows(HandyFlowException.class, () -> service.close(T, c.id(), new CloseRequest("too short"), MANAGER, "Mgr"));
        assertEquals(CampaignStatus.OPEN, service.detail(T, c.id()).campaign().status());
        CampaignResponse closed = service.close(T, c.id(), new CloseRequest("Annual round finished and results reported"), MANAGER, "Mgr");
        assertEquals(CampaignStatus.CLOSED, closed.status());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertThrows(HandyFlowException.class, () -> service.declare(T, returnOf(ANNA), interest(), ANNA, "Anna"));
        assertEquals(0, coiStore.size(), "nothing is declared into a closed campaign");
        assertThrows(HandyFlowException.class, () -> service.close(T, c.id(), new CloseRequest("Closing it a second time for no reason"), MANAGER, "Mgr"));
        assertFalse(service.mine(T, ANNA).get(0).campaignOpen());
    }

    @Test @DisplayName("Reminders go to people who have not answered, only on reminder days")
    void reminders() {
        CampaignResponse c = launch();
        DeclarationCampaign camp = campaignStore.get(0);
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna");
        sent.clear(); sentTo.clear();
        assertEquals(0, service.remind(T, camp, camp.getDueDate().minusDays(10)));
        assertEquals(0, sent.size());
        assertEquals(2, service.remind(T, camp, camp.getDueDate().minusDays(7)));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).startsWith(NotificationType.LEGALCOMPLIANCE_DECLARATION_REMINDER + "|"));
        assertEquals(Set.of(BEN, CARA), new HashSet<>(sentTo.get(0).stream().map(Recipient::userId).toList()));
        assertEquals(2, service.remind(T, camp, camp.getDueDate().plusDays(7)));
        service.answer(T, returnOf(BEN), new AnswerRequest(Answer.NIL), BEN, "Ben");
        service.answer(T, returnOf(CARA), new AnswerRequest(Answer.NIL), CARA, "Cara");
        sent.clear();
        assertEquals(0, service.remind(T, camp, camp.getDueDate()), "everyone has answered");
        assertEquals(0, sent.size());
        assertEquals(100, service.detail(T, c.id()).campaign().percentComplete());
    }

    @Test @DisplayName("A closed campaign sends no reminders")
    void closedNoReminders() {
        CampaignResponse c = launch();
        service.close(T, c.id(), new CloseRequest("Annual round finished and results reported"), MANAGER, "Mgr");
        sent.clear();
        assertEquals(0, service.remind(T, campaignStore.get(0), campaignStore.get(0).getDueDate()));
        assertEquals(0, sent.size());
    }

    @Test @DisplayName("The manager's view has who answered what and never the content of a declaration")
    void managerViewHasNoContent() {
        CampaignResponse c = launch();
        service.declare(T, returnOf(ANNA), interest(), ANNA, "Anna");
        CampaignDetail d = service.detail(T, c.id());
        ReturnRow row = d.returns().stream().filter(r -> r.userName().equals("Anna")).findFirst().orElseThrow();
        assertEquals(ReturnStatus.DECLARED, row.status());
        for (java.lang.reflect.RecordComponent rc : ReturnRow.class.getRecordComponents()) assertFalse(rc.getName().toLowerCase().contains("counterparty") || rc.getName().toLowerCase().contains("description"), rc.getName());
        for (String line : audit) assertFalse(line.contains("Acme"), line);
    }

    // ── Chunk 22: overdue digest and new joiners ──

    private int overdueNotices() { return (int) sent.stream().filter(x -> x.startsWith(NotificationType.LEGALCOMPLIANCE_DECLARATION_OVERDUE + "|")).count(); }

    @Test @DisplayName("Once overdue, whoever launched the campaign gets the count of people still outstanding, in the same weekly rhythm as the reminders")
    void overdueDigest() {
        launch();
        DeclarationCampaign camp = campaignStore.get(0);
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna");
        sent.clear(); sentTo.clear();
        service.remind(T, camp, camp.getDueDate().minusDays(7));
        service.remind(T, camp, camp.getDueDate());
        assertEquals(0, overdueNotices(), "not overdue yet, on or before the due date");
        sent.clear(); sentTo.clear();
        assertEquals(0, service.remind(T, camp, camp.getDueDate().plusDays(3)));
        assertEquals(0, overdueNotices(), "a day that is not a reminder day sends nothing");
        assertEquals(0, sent.size());
        assertEquals(2, service.remind(T, camp, camp.getDueDate().plusDays(7)));
        assertEquals(2, sent.size(), "one reminder to the pending people, one digest to the owner");
        int i = sent.indexOf(sent.stream().filter(x -> x.startsWith(NotificationType.LEGALCOMPLIANCE_DECLARATION_OVERDUE + "|")).findFirst().orElseThrow());
        assertTrue(sent.get(i).contains("2 of 3 not answered"), sent.get(i));
        assertEquals(List.of(MANAGER), sentTo.get(i).stream().map(Recipient::userId).toList());
        for (String line : sent) for (String name : List.of("Ben", "Cara")) assertFalse(line.contains(name), "the digest carries counts, not names: " + line);
    }

    @Test @DisplayName("Nothing outstanding means no digest, and a closed campaign sends none")
    void noDigestWhenNothingToChase() {
        CampaignResponse c = launch();
        DeclarationCampaign camp = campaignStore.get(0);
        for (UUID u : List.of(ANNA, BEN, CARA)) service.answer(T, returnOf(u), new AnswerRequest(Answer.NIL), u, "x");
        sent.clear();
        assertEquals(0, service.remind(T, camp, camp.getDueDate().plusDays(7)));
        assertEquals(0, sent.size());
        returnStore.clear(); campaignStore.clear(); sent.clear();
        CampaignResponse c2 = launch();
        service.close(T, c2.id(), new CloseRequest("Annual round finished and results reported"), MANAGER, "Mgr");
        sent.clear();
        assertEquals(0, service.remind(T, campaignStore.get(0), campaignStore.get(0).getDueDate().plusDays(7)));
        assertEquals(0, sent.size());
    }

    @Test @DisplayName("If the owner cannot be found the reminders still go out, and an empty owner list sends no digest")
    void ownerLookupProblems() {
        launch();
        DeclarationCampaign camp = campaignStore.get(0);
        ownersFail = true; sent.clear();
        assertEquals(3, service.remind(T, camp, camp.getDueDate().plusDays(7)));
        assertEquals(1, sent.size());
        assertEquals(0, overdueNotices());
        ownersFail = false; ownerList = List.of(); sent.clear();
        assertEquals(3, service.remind(T, camp, camp.getDueDate().plusDays(14)));
        assertEquals(0, overdueNotices());
    }

    @Test @DisplayName("New joiners are added and asked; people already in the campaign, whatever they answered, are left alone")
    void addsNewJoiners() {
        CampaignResponse c = launch();
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna");
        service.declare(T, returnOf(BEN), interest(), BEN, "Ben");
        UUID dina = UUID.randomUUID();
        people = List.of(people.get(0), people.get(1), people.get(2), new UserDirectory.Person(dina, "Dina", "dina@x.co.za"));
        sent.clear(); sentTo.clear(); audit.clear();
        SyncResult r = service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(1, r.added());
        assertEquals(4, r.campaign().total());
        assertEquals(2, r.campaign().pending());
        assertEquals(4, returnStore.size());
        assertEquals(ReturnStatus.NIL, returnStore.stream().filter(x -> x.getUserId().equals(ANNA)).findFirst().orElseThrow().getStatus());
        assertEquals(ReturnStatus.DECLARED, returnStore.stream().filter(x -> x.getUserId().equals(BEN)).findFirst().orElseThrow().getStatus());
        assertEquals(1, sent.size());
        assertEquals(List.of(dina), sentTo.get(0).stream().map(Recipient::userId).toList(), "only the new person is notified");
        assertTrue(audit.get(0).startsWith("DECLARATION_CAMPAIGN|AUDIENCE_UPDATED|DEC-00001: 1 added, 0 excused"), audit.get(0));
        assertEquals(1, service.mine(T, dina).size());
    }

    @Test @DisplayName("Running it again with nobody new adds nothing and tells nobody")
    void nothingNew() {
        CampaignResponse c = launch();
        sent.clear(); audit.clear();
        SyncResult r = service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(0, r.added());
        assertEquals(3, returnStore.size());
        assertEquals(0, sent.size());
        assertEquals(0, audit.size());
    }

    @Test @DisplayName("The campaign's own department is used, and a closed campaign cannot be extended")
    void departmentAndClosed() {
        CampaignResponse c = service.launch(T, new CampaignRequest("Finance only", null, TODAY.plusDays(14), "Finance"), MANAGER, "Mgr");
        askedDepartments.clear();
        service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(List.of("Finance"), askedDepartments);
        service.close(T, c.id(), new CloseRequest("Finance round finished and results reported"), MANAGER, "Mgr");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.updateAudience(T, c.id(), MANAGER, "Mgr"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class, () -> service.updateAudience(T, UUID.randomUUID(), MANAGER, "Mgr"));
    }

    // ── Chunk 25: "unchanged" extends review dates ──

    private CoiDeclaration decided(UUID user, DeclarationStatus outcome) {
        existingCleared(user, outcome);
        return coiStore.get(coiStore.size() - 1);
    }

    private CoiDeclaration awaiting(UUID user, boolean underReview) {
        CoiDeclaration d = CoiDeclaration.declare(T, "COI-NEW" + coiStore.size(), user, "X", DeclarationType.RELATED_PARTY, "Cousin's firm", "My cousin owns a firm that tenders to us", null, TODAY.minusDays(20), 12);
        if (underReview) d.startReview(UUID.randomUUID(), "Rev");
        coiStore.add(d);
        return d;
    }

    @Test @DisplayName("Saying 'unchanged' moves the review date of each cleared or mitigated declaration a full period from today, and says how many")
    void unchangedExtends() {
        launch();
        CoiDeclaration cleared = decided(ANNA, DeclarationStatus.CLEARED), mitigated = decided(ANNA, DeclarationStatus.MITIGATED);
        LocalDate before = cleared.getNextReviewDue();
        assertTrue(before.isBefore(TODAY.plusMonths(12)), "it moves later");
        audit.clear();
        MyReturn r = service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna");
        assertEquals(2, r.reviewDatesExtended());
        assertEquals(TODAY.plusMonths(12), cleared.getNextReviewDue());
        assertEquals(TODAY.plusMonths(12), mitigated.getNextReviewDue());
        assertEquals(2, audit.stream().filter(l -> l.startsWith("COI_DECLARATION|CONFIRMED_UNCHANGED|")).count());
        assertTrue(audit.stream().anyMatch(l -> l.startsWith("DECLARATION_RETURN|UNCHANGED|DEC-00001 — 2 review date(s) extended")), audit.toString());
        assertEquals(0, service.mine(T, ANNA).get(0).reviewDatesExtended(), "other views do not report it");
    }

    @Test @DisplayName("Declarations still awaiting review have no review date and are left alone, but still count as earlier declarations")
    void awaitingLeftAlone() {
        launch();
        CoiDeclaration fresh = awaiting(ANNA, false), underReview = awaiting(ANNA, true);
        CoiDeclaration cleared = decided(ANNA, DeclarationStatus.CLEARED);
        MyReturn r = service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna");
        assertEquals(1, r.reviewDatesExtended());
        assertNull(fresh.getNextReviewDue());
        assertNull(underReview.getNextReviewDue());
        assertEquals(TODAY.plusMonths(12), cleared.getNextReviewDue());
        // only awaiting ones: unchanged is accepted, nothing extended
        returnStore.clear(); campaignStore.clear(); coiStore.clear(); launch();
        awaiting(BEN, false);
        MyReturn only = service.answer(T, returnOf(BEN), new AnswerRequest(Answer.UNCHANGED), BEN, "Ben");
        assertEquals(ReturnStatus.UNCHANGED, only.status());
        assertEquals(0, only.reviewDatesExtended());
    }

    @Test @DisplayName("Only the person's own declarations are touched, and 'nothing to declare' extends nothing")
    void ownOnly() {
        launch();
        CoiDeclaration bens = decided(BEN, DeclarationStatus.CLEARED), annas = decided(ANNA, DeclarationStatus.CLEARED);
        LocalDate bensBefore = bens.getNextReviewDue();
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna");
        assertEquals(bensBefore, bens.getNextReviewDue());
        CoiDeclaration caras = decided(CARA, DeclarationStatus.CLEARED);
        LocalDate carasBefore = caras.getNextReviewDue();
        MyReturn nil = service.answer(T, returnOf(CARA), new AnswerRequest(Answer.NIL), CARA, "Cara");
        assertEquals(0, nil.reviewDatesExtended());
        assertEquals(carasBefore, caras.getNextReviewDue(), "'nothing to declare' says nothing about earlier declarations");
        assertEquals(TODAY.plusMonths(12), annas.getNextReviewDue());
    }

    @Test @DisplayName("Answering 'unchanged' twice on the same day extends once and adds the confirmation note once")
    void idempotent() {
        launch();
        CoiDeclaration d = decided(ANNA, DeclarationStatus.CLEARED);
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna");
        audit.clear();
        MyReturn again = service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna");
        assertEquals(0, again.reviewDatesExtended());
        assertEquals(1, d.getDecisionNote().split("Declarant confirmed nothing has changed").length - 1);
        assertEquals(0, audit.stream().filter(l -> l.startsWith("COI_DECLARATION|CONFIRMED_UNCHANGED|")).count());
    }

    @Test @DisplayName("A failed 'unchanged' (no earlier declarations) extends nothing and leaves the return pending")
    void failedUnchanged() {
        launch();
        assertThrows(HandyFlowException.class, () -> service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna"));
        assertEquals(ReturnStatus.PENDING, returnStore.stream().filter(x -> x.getUserId().equals(ANNA)).findFirst().orElseThrow().getStatus());
    }

    @Test @DisplayName("The audit lines for extensions say which declaration, never what it was about")
    void auditHasNoContent() {
        launch();
        decided(ANNA, DeclarationStatus.CLEARED);
        audit.clear();
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.UNCHANGED), ANNA, "Anna");
        for (String line : audit) { assertFalse(line.contains("Old employer"), line); assertFalse(line.contains("consulting"), line); }
    }

    // ── Chunk 26: leavers ──

    private UserDirectory.Person person(UUID id) { String n = id.equals(ANNA) ? "Anna" : id.equals(BEN) ? "Ben" : "Cara"; return new UserDirectory.Person(id, n, n.toLowerCase() + "@x.co.za"); }

    private DeclarationReturn ret(UUID user) { return returnStore.stream().filter(r -> r.getUserId().equals(user)).findFirst().orElseThrow(); }

    @Test @DisplayName("Someone who has left and not answered is excused: not counted, not chased; someone who answered then left stays on the record")
    void leaversExcused() {
        CampaignResponse c = launch();
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna");
        people = List.of(person(BEN));   // Anna answered and left, Cara left without answering
        audit.clear();
        SyncResult r = service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(1, r.excused());
        assertEquals(0, r.added());
        assertEquals(ReturnStatus.LEFT, ret(CARA).getStatus());
        assertEquals(ReturnStatus.NIL, ret(ANNA).getStatus(), "an answer is a record");
        assertEquals(2, r.campaign().total(), "excused people are not counted");
        assertEquals(1, r.campaign().left());
        assertEquals(1, r.campaign().pending());
        assertEquals(50, r.campaign().percentComplete());
        assertTrue(audit.get(0).startsWith("DECLARATION_CAMPAIGN|AUDIENCE_UPDATED|DEC-00001: 0 added, 1 excused"), audit.get(0));
        for (String line : audit) assertFalse(line.contains("Cara"), line);
    }

    @Test @DisplayName("Someone who is back is put back, and can answer again")
    void returnersReinstated() {
        CampaignResponse c = launch();
        people = List.of(person(ANNA), person(BEN));
        service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(ReturnStatus.LEFT, ret(CARA).getStatus());
        people = List.of(person(ANNA), person(BEN), new UserDirectory.Person(CARA, "Cara", "cara@x.co.za"));
        SyncResult r = service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(1, r.reinstated());
        assertEquals(0, r.added(), "she already has a return, so she is not added twice");
        assertEquals(3, returnStore.size());
        assertEquals(ReturnStatus.PENDING, ret(CARA).getStatus());
        assertEquals(ReturnStatus.NIL, service.answer(T, returnOf(CARA), new AnswerRequest(Answer.NIL), CARA, "Cara").status());
    }

    @Test @DisplayName("An empty or failed directory never excuses anyone")
    void emptyDirectoryChangesNothing() {
        CampaignResponse c = launch();
        people = List.of();
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.updateAudience(T, c.id(), MANAGER, "Mgr"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(3L, returnStore.stream().filter(DeclarationReturn::isPending).count());
        DeclarationCampaign camp = campaignStore.get(0);
        service.remind(T, camp, camp.getDueDate().minusDays(7));
        assertEquals(3L, returnStore.stream().filter(DeclarationReturn::isPending).count(), "the overnight sweep also leaves everyone alone");
        directoryFails = true;
        service.remind(T, camp, camp.getDueDate().minusDays(7));
        assertEquals(3L, returnStore.stream().filter(DeclarationReturn::isPending).count());
    }

    @Test @DisplayName("Overnight, leavers are excused before reminders go out, so only people still in the audience are chased and the overdue count excludes them")
    void overnightExcuses() {
        launch();
        DeclarationCampaign camp = campaignStore.get(0);
        people = List.of(person(ANNA), person(BEN));   // Cara left
        sent.clear(); sentTo.clear();
        assertEquals(2, service.remind(T, camp, camp.getDueDate().plusDays(7)));
        assertEquals(ReturnStatus.LEFT, ret(CARA).getStatus());
        assertEquals(Set.of(ANNA, BEN), new HashSet<>(sentTo.get(0).stream().map(Recipient::userId).toList()));
        int i = sent.indexOf(sent.stream().filter(x -> x.startsWith(NotificationType.LEGALCOMPLIANCE_DECLARATION_OVERDUE + "|")).findFirst().orElseThrow());
        assertTrue(sent.get(i).contains("2 of 2 not answered"), sent.get(i));
        // excusing on a day that is not a reminder day still happens, and sends nothing
        returnStore.clear(); campaignStore.clear(); sent.clear(); people = List.of(person(ANNA), person(BEN), person(CARA));
        launch();
        sent.clear();
        people = List.of(person(ANNA), person(BEN));
        assertEquals(0, service.remind(T, campaignStore.get(0), campaignStore.get(0).getDueDate().minusDays(10)));
        assertEquals(ReturnStatus.LEFT, ret(CARA).getStatus());
        assertEquals(0, sent.size());
    }

    @Test @DisplayName("A campaign where everyone still in the audience has answered stops being chased even though a leaver never did")
    void leaverDoesNotHoldCampaignOpen() {
        CampaignResponse c = launch();
        DeclarationCampaign camp = campaignStore.get(0);
        service.answer(T, returnOf(ANNA), new AnswerRequest(Answer.NIL), ANNA, "Anna");
        service.answer(T, returnOf(BEN), new AnswerRequest(Answer.NIL), BEN, "Ben");
        people = List.of(person(ANNA), person(BEN));
        sent.clear();
        assertEquals(0, service.remind(T, camp, camp.getDueDate().plusDays(7)));
        assertEquals(0, sent.size());
        assertEquals(100, service.detail(T, c.id()).campaign().percentComplete());
    }

    @Test @DisplayName("A person who has been excused cannot answer or declare")
    void leftCannotAnswer() {
        CampaignResponse c = launch();
        people = List.of(person(ANNA), person(BEN));
        service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(HttpStatus.CONFLICT, assertThrows(HandyFlowException.class, () -> service.answer(T, returnOf(CARA), new AnswerRequest(Answer.NIL), CARA, "Cara")).getStatus());
        assertThrows(HandyFlowException.class, () -> service.declare(T, returnOf(CARA), interest(), CARA, "Cara"));
        assertEquals(0, coiStore.size());
    }

    @Test @DisplayName("Joiners, leavers and returners can all change in one update, and a no-change update says nothing and records nothing")
    void combined() {
        CampaignResponse c = launch();
        UUID dina = UUID.randomUUID();
        people = List.of(person(ANNA), new UserDirectory.Person(dina, "Dina", "dina@x.co.za"));   // Ben and Cara left, Dina joined
        SyncResult r = service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(1, r.added());
        assertEquals(2, r.excused());
        assertEquals(2, r.campaign().total());
        sent.clear(); audit.clear();
        SyncResult again = service.updateAudience(T, c.id(), MANAGER, "Mgr");
        assertEquals(0, again.added() + again.excused() + again.reinstated());
        assertEquals(0, sent.size());
        assertEquals(0, audit.size());
    }
}
