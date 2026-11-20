package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.WrittenResolutionDtos.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The written-resolution flow end to end, with in-memory stand-ins for the repositories, notifications and audit trail. */
class WrittenResolutionServiceTest {

    private static final TenantId T = TenantId.of(UUID.randomUUID());
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    private static final UUID ANNA_U = UUID.randomUUID(), BEN_U = UUID.randomUUID(), OBI_U = UUID.randomUUID(), STRANGER = UUID.randomUUID(), MGR = UUID.randomUUID();

    private final List<WrittenResolution> resolutionStore = new ArrayList<>();
    private final List<WrittenResponse> responseStore = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    private final List<Collection<UUID>> sentTo = new ArrayList<>();
    private Committee committee;
    private CommitteeMember anna, ben, cara, obi, former;
    private WrittenResolutionService service;
    private long ref;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> {
            if (m.getName().equals("toString")) return type.getSimpleName();
            return handler.apply(m.getName(), args == null ? new Object[0] : args);
        });
    }

    @BeforeEach
    void setUp() {
        LocalDate long_ago = TODAY.minusDays(200);
        committee = Committee.create(T, "COM-00001", "Risk committee", CommitteeType.RISK, null, 3, 2, 12, null, "me");
        anna = CommitteeMember.add(T, committee.getId(), "Anna", ANNA_U, MemberRole.CHAIR, long_ago, TODAY);
        ben = CommitteeMember.add(T, committee.getId(), "Ben", BEN_U, MemberRole.MEMBER, long_ago, TODAY);
        cara = CommitteeMember.add(T, committee.getId(), "Cara", null, MemberRole.MEMBER, long_ago, TODAY);
        obi = CommitteeMember.add(T, committee.getId(), "Obi", OBI_U, MemberRole.OBSERVER, long_ago, TODAY);
        former = CommitteeMember.add(T, committee.getId(), "Fred", UUID.randomUUID(), MemberRole.MEMBER, long_ago, TODAY);
        former.end(TODAY.minusDays(30), TODAY);
        List<CommitteeMember> all = List.of(anna, ben, cara, obi, former);
        CommitteeRepository committees = proxy(CommitteeRepository.class, (n, a) -> n.equals("findForTenant") ? Optional.of(committee) : null);
        CommitteeMemberRepository members = proxy(CommitteeMemberRepository.class, (n, a) -> n.equals("findForCommittee") ? all : null);
        WrittenResolutionRepository resolutions = proxy(WrittenResolutionRepository.class, (n, a) -> switch (n) {
            case "findForTenant" -> resolutionStore.stream().filter(w -> w.getId().equals(a[1])).findFirst();
            case "search" -> resolutionStore;
            case "findForIds" -> resolutionStore.stream().filter(w -> ((Collection<?>) a[1]).contains(w.getId())).toList();
            case "findOpenAcrossTenants" -> resolutionStore.stream().filter(WrittenResolution::isOpen).toList();
            case "nextRef" -> ++ref;
            case "save" -> { if (!resolutionStore.contains(a[0])) resolutionStore.add((WrittenResolution) a[0]); yield a[0]; }
            default -> null;
        });
        WrittenResponseRepository responses = proxy(WrittenResponseRepository.class, (n, a) -> switch (n) {
            case "findForResolution" -> responseStore.stream().filter(r -> r.getResolutionId().equals(a[1])).sorted(Comparator.comparing(WrittenResponse::getMemberName)).toList();
            case "findForUser" -> responseStore.stream().filter(r -> a[1].equals(r.getUserId())).toList();
            case "saveAll" -> { for (Object o : (Iterable<?>) a[0]) responseStore.add((WrittenResponse) o); yield a[0]; }
            case "save" -> { if (!responseStore.contains(a[0])) responseStore.add((WrittenResponse) a[0]); yield a[0]; }
            default -> null;
        });
        WrittenResolutionNotifier notifier = (t, type, title, message, ids) -> { sent.add(type + "|" + title); sentTo.add(new ArrayList<>(ids)); };
        RegulatoryAuditService trail = new RegulatoryAuditService(null) {
            @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID a, String n) { audit.add(ev + "|" + d); }
        };
        service = new WrittenResolutionService(committees, members, resolutions, responses, notifier, trail);
    }

    private WrittenDetail circulate(ApprovalThreshold th) {
        return service.circulate(T, new CirculateRequest(committee.getId(), "Approve the anti-bribery policy", "The committee approves the revised anti-bribery policy as circulated", th, TODAY.plusDays(7)), MGR, "Sec");
    }

    private UUID id() { return resolutionStore.get(0).getId(); }

    private void answer(UUID user, WrittenChoice c) { service.respond(T, id(), new RespondRequest(c, null), user, "x"); }

    private void recordCara(WrittenChoice c) { service.recordOnBehalf(T, id(), cara.getId(), new RecordRequest(c, null, "Email from Cara dated today"), MGR, "Sec"); }

    @Test @DisplayName("Circulating sends it to the voting members only (not observers or people who left) and tells those with accounts")
    void circulates() {
        WrittenDetail d = circulate(ApprovalThreshold.UNANIMOUS);
        assertEquals("WRS-00001", d.resolution().reference());
        assertEquals(3, d.resolution().memberCount());
        assertEquals(List.of("Anna", "Ben", "Cara"), d.members().stream().map(MemberRow::name).toList());
        assertEquals(0, d.resolution().answered());
        assertFalse(d.members().stream().filter(m -> m.name().equals("Cara")).findFirst().orElseThrow().hasAccount());
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).startsWith(NotificationType.LEGALCOMPLIANCE_WRITTEN_RESOLUTION_REQUEST + "|"));
        assertEquals(Set.of(ANNA_U, BEN_U), new HashSet<>(sentTo.get(0)), "Cara has no account, Obi observes, Fred left");
        assertTrue(audit.get(0).startsWith("WRITTEN_CIRCULATED|WRS-00001") && audit.get(0).contains("3 member(s)"));
    }

    @Test @DisplayName("It cannot be circulated for a dissolved committee or one with too few voting members")
    void circulateRefused() {
        committee.dissolve("The committee has been merged into audit");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> circulate(ApprovalThreshold.UNANIMOUS));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(0, resolutionStore.size());
    }

    @Test @DisplayName("A member answers for themselves, can change the answer while open, and nobody can answer for another")
    void respond() {
        circulate(ApprovalThreshold.UNANIMOUS);
        MyWritten r = service.respond(T, id(), new RespondRequest(WrittenChoice.AGAINST, "Needs more work"), BEN_U, "Ben");
        assertEquals(WrittenChoice.AGAINST, r.myChoice());
        service.respond(T, id(), new RespondRequest(WrittenChoice.FOR, null), BEN_U, "Ben");
        assertEquals(WrittenChoice.FOR, service.mine(T, BEN_U).get(0).myChoice());
        assertThrows(ResourceNotFoundException.class, () -> service.respond(T, id(), new RespondRequest(WrittenChoice.FOR, null), STRANGER, "Stranger"));
        assertThrows(ResourceNotFoundException.class, () -> service.respond(T, id(), new RespondRequest(WrittenChoice.FOR, null), OBI_U, "Obi"), "an observer was not sent it");
        assertEquals(0, service.mine(T, STRANGER).size());
        assertEquals(1, service.mine(T, ANNA_U).size());
    }

    @Test @DisplayName("While open, managers see who answered but not how; once closed, the answers are shown")
    void choicesHiddenWhileOpen() {
        circulate(ApprovalThreshold.MAJORITY_OF_ALL);
        answer(ANNA_U, WrittenChoice.FOR);
        WrittenDetail open = service.detail(T, id());
        MemberRow anna = open.members().stream().filter(m -> m.name().equals("Anna")).findFirst().orElseThrow();
        assertTrue(anna.answered());
        assertNull(anna.choice());
        assertNull(open.resolution().votesFor(), "no interim tally either");
        assertEquals(1, open.resolution().answered());
        answer(BEN_U, WrittenChoice.FOR);
        recordCara(WrittenChoice.ABSTAIN);
        service.close(T, id(), MGR, "Sec");
        WrittenDetail closed = service.detail(T, id());
        assertEquals(WrittenChoice.FOR, closed.members().stream().filter(m -> m.name().equals("Anna")).findFirst().orElseThrow().choice());
        assertEquals(Integer.valueOf(2), closed.resolution().votesFor());
        assertEquals(Integer.valueOf(1), closed.resolution().abstained());
    }

    @Test @DisplayName("Majority of all: 2 of 3 is carried even if the third abstained")
    void majorityCarried() {
        circulate(ApprovalThreshold.MAJORITY_OF_ALL);
        answer(ANNA_U, WrittenChoice.FOR); answer(BEN_U, WrittenChoice.FOR); recordCara(WrittenChoice.ABSTAIN);
        WrittenDetail d = service.close(T, id(), MGR, "Sec");
        assertEquals(WrittenStatus.CARRIED, d.resolution().status());
        assertFalse(d.resolution().closedAutomatically());
        assertEquals(LocalDate.now(ZoneId.of("Africa/Johannesburg")), d.resolution().closedOn());
    }

    @Test @DisplayName("Unanimous: one member against means not carried")
    void unanimousFails() {
        circulate(ApprovalThreshold.UNANIMOUS);
        answer(ANNA_U, WrittenChoice.FOR); answer(BEN_U, WrittenChoice.FOR); recordCara(WrittenChoice.AGAINST);
        assertEquals(WrittenStatus.NOT_CARRIED, service.close(T, id(), MGR, "Sec").resolution().status());
    }

    @Test @DisplayName("It cannot be closed by hand while members have not answered and the deadline has not passed")
    void closeNeedsEveryone() {
        circulate(ApprovalThreshold.MAJORITY_OF_ALL);
        answer(ANNA_U, WrittenChoice.FOR); answer(BEN_U, WrittenChoice.FOR);
        WrittenDetail d = service.detail(T, id());
        assertFalse(d.resolution().canClose());
        assertTrue(d.resolution().closeProblem().contains("1 member(s)"));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.close(T, id(), MGR, "Sec"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals(WrittenStatus.OPEN, resolutionStore.get(0).getStatus());
        recordCara(WrittenChoice.FOR);
        assertTrue(service.detail(T, id()).resolution().canClose());
    }

    @Test @DisplayName("A member with an account cannot have an answer recorded for them; a recorded answer needs to say how it was received")
    void recordRules() {
        circulate(ApprovalThreshold.UNANIMOUS);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.recordOnBehalf(T, id(), ben.getId(), new RecordRequest(WrittenChoice.FOR, null, "Phoned me this morning"), MGR, "Sec"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        HandyFlowException s = assertThrows(HandyFlowException.class, () -> service.recordOnBehalf(T, id(), cara.getId(), new RecordRequest(WrittenChoice.FOR, null, "short"), MGR, "Sec"));
        assertEquals(HttpStatus.BAD_REQUEST, s.getStatus());
        assertThrows(ResourceNotFoundException.class, () -> service.recordOnBehalf(T, id(), obi.getId(), new RecordRequest(WrittenChoice.FOR, null, "Email from Obi today"), MGR, "Sec"));
        recordCara(WrittenChoice.FOR);
        MemberRow row = service.detail(T, id()).members().stream().filter(m -> m.name().equals("Cara")).findFirst().orElseThrow();
        assertEquals("Sec", row.recordedByName());
        assertEquals("Email from Cara dated today", row.howReceived());
    }

    @Test @DisplayName("Cancelling needs a reason, tells the members, and ends all answering")
    void cancel() {
        circulate(ApprovalThreshold.UNANIMOUS);
        assertThrows(HandyFlowException.class, () -> service.cancel(T, id(), new CancelRequest("too short"), MGR, "Sec"));
        sent.clear(); sentTo.clear();
        WrittenDetail d = service.cancel(T, id(), new CancelRequest("Sent to the wrong committee by mistake"), MGR, "Sec");
        assertEquals(WrittenStatus.CANCELLED, d.resolution().status());
        assertEquals(1, sent.size());
        assertEquals(Set.of(ANNA_U, BEN_U), new HashSet<>(sentTo.get(0)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(HandyFlowException.class, () -> answer(ANNA_U, WrittenChoice.FOR)).getStatus());
        assertThrows(HandyFlowException.class, () -> service.close(T, id(), MGR, "Sec"));
        assertThrows(HandyFlowException.class, () -> recordCara(WrittenChoice.FOR));
    }

    @Test @DisplayName("After the deadline the overnight sweep closes it with silence counted against, and does so only once")
    void sweepCloses() {
        circulate(ApprovalThreshold.MAJORITY_OF_ALL);
        answer(ANNA_U, WrittenChoice.FOR);
        WrittenResolution w = resolutionStore.get(0);
        assertFalse(service.sweep(T, w, w.getDeadline()), "answers are still welcome on the deadline day");
        assertTrue(w.isOpen());
        sent.clear();
        assertTrue(service.sweep(T, w, w.getDeadline().plusDays(1)));
        assertEquals(WrittenStatus.NOT_CARRIED, w.getStatus(), "1 of 3 for");
        assertTrue(w.isClosedAutomatically());
        assertEquals(Integer.valueOf(1), w.getVotesFor());
        assertEquals(Integer.valueOf(2), w.getNoResponse());
        assertTrue(audit.get(audit.size() - 1).contains("closed automatically at the deadline"));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).startsWith(NotificationType.LEGALCOMPLIANCE_WRITTEN_RESOLUTION_CLOSED + "|"));
        assertFalse(service.sweep(T, w, w.getDeadline().plusDays(2)));
        assertEquals(1, sent.size());
    }

    @Test @DisplayName("Reminders go to members with accounts who have not answered, on reminder days only")
    void sweepReminds() {
        circulate(ApprovalThreshold.UNANIMOUS);
        answer(ANNA_U, WrittenChoice.FOR);
        WrittenResolution w = resolutionStore.get(0);
        sent.clear(); sentTo.clear();
        assertFalse(service.sweep(T, w, w.getDeadline().minusDays(4)));
        assertEquals(0, sent.size());
        assertFalse(service.sweep(T, w, w.getDeadline().minusDays(2)));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).startsWith(NotificationType.LEGALCOMPLIANCE_WRITTEN_RESOLUTION_REMINDER + "|"));
        assertEquals(List.of(BEN_U), new ArrayList<>(sentTo.get(0)), "Anna answered; Cara has no account");
        assertTrue(w.isOpen());
    }

    @Test @DisplayName("The scheduler closes what is due and counts it")
    void scheduler() {
        circulate(ApprovalThreshold.UNANIMOUS);
        WrittenResolutionRepository repo = proxy(WrittenResolutionRepository.class, (n, a) -> n.equals("findOpenAcrossTenants") ? resolutionStore.stream().filter(WrittenResolution::isOpen).toList() : null);
        WrittenResolutionScheduler s = new WrittenResolutionScheduler(repo, service);
        assertEquals(0, s.run(TODAY));
        assertEquals(1, s.run(TODAY.plusDays(8)));
        assertEquals(0, s.run(TODAY.plusDays(9)));
    }

    @Test @DisplayName("The audit trail has counts and outcomes and names nobody")
    void auditNamesNobody() {
        circulate(ApprovalThreshold.MAJORITY_OF_ALL);
        answer(ANNA_U, WrittenChoice.AGAINST); answer(BEN_U, WrittenChoice.FOR); recordCara(WrittenChoice.FOR);
        service.close(T, id(), MGR, "Sec");
        assertEquals(List.of("WRITTEN_CIRCULATED", "WRITTEN_RESPONDED", "WRITTEN_RESPONDED", "WRITTEN_RECORDED", "WRITTEN_CLOSED"), audit.stream().map(l -> l.substring(0, l.indexOf('|'))).toList());
        assertTrue(audit.get(1).contains("(1 of 3 answered)") && audit.get(2).contains("(2 of 3 answered)"));
        assertTrue(audit.get(4).contains("CARRIED (2 for, 1 against, 0 abstained, 0 no response of 3)"));
        for (String line : audit) for (String n : new String[]{"Anna", "Ben", "Cara"}) assertFalse(line.contains(n), line);
    }
}
