package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conflict checks and the gate on opening a matter, against in-memory stand-ins for the repositories (no database, no
 * Spring). The migration's constraints and triggers were run against PostgreSQL separately.
 */
class LpConflictCheckServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID runner = UUID.randomUUID();
    private final UUID reviewer = UUID.randomUUID();

    private final Map<UUID, LpClient> clients = new LinkedHashMap<>();
    private final Map<UUID, LpMatter> matters = new LinkedHashMap<>();
    private final Map<UUID, LpConflictCheck> checks = new LinkedHashMap<>();
    private final List<LpConflictCheckSubject> subjects = new ArrayList<>();
    private final List<LpConflictCheckHit> hits = new ArrayList<>();
    private final List<LpMatterParty> parties = new ArrayList<>();
    private final List<LpTrustSettingChange> settingRows = new ArrayList<>();

    private LpConflictCheckService service;
    private LpMatterService matterService;
    private LpTrustSettingsService settings;

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

    private static String like(Object pattern) { return ((String) pattern).replace("%", "").toLowerCase(); }

    @BeforeEach
    void setUp() {
        clients.clear(); matters.clear(); checks.clear(); subjects.clear(); hits.clear(); parties.clear(); settingRows.clear();
        var clientRepo = fake(LpClientRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(clients.get((UUID) a[1])).filter(c -> c.getTenantId().equals(a[0]))));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(matters.get((UUID) a[1])).filter(m -> m.getTenantId().equals(a[0])),
                "save", a -> { LpMatter m = (LpMatter) a[0]; matters.put(m.getId(), m); return m; }));
        var trustRepo = fake(LpTrustTransactionRepository.class, Map.of("netHeldForMatter", a -> java.math.BigDecimal.ZERO));
        var clientSearch = fake(LpConflictClientSearchRepository.class, Map.of(
                "candidatesByName", a -> clients.values().stream().filter(c -> c.getTenantId().equals(a[0]) && c.getName().toLowerCase().contains(like(a[1]))).limit(LpConflictCheckService.CANDIDATE_LIMIT).toList(),
                "byIdNumber", a -> clients.values().stream().filter(c -> c.getTenantId().equals(a[0]) && c.getIdOrRegistrationNumber() != null
                        && ConflictMatcher.normalizeId(c.getIdOrRegistrationNumber()).equals(a[1])).toList()));
        var partyRepo = fake(LpMatterPartyRepository.class, Map.of(
                "candidatesByName", a -> parties.stream().filter(p -> p.getTenantId().equals(a[0]) && p.getName().toLowerCase().contains(like(a[1]))).limit(LpConflictCheckService.CANDIDATE_LIMIT).toList(),
                "byIdNumber", a -> parties.stream().filter(p -> p.getTenantId().equals(a[0]) && p.getIdNumber() != null
                        && ConflictMatcher.normalizeId(p.getIdNumber()).equals(a[1])).toList(),
                "save", a -> { parties.add((LpMatterParty) a[0]); return a[0]; }));
        var checkRepo = fake(LpConflictCheckRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(checks.get((UUID) a[1])).filter(c -> c.getTenantId().equals(a[0])),
                "save", a -> { LpConflictCheck c = (LpConflictCheck) a[0]; checks.put(c.getId(), c); return c; }));
        var subjectRepo = fake(LpConflictCheckSubjectRepository.class, Map.of(
                "findByCheck", a -> subjects.stream().filter(s -> s.getTenantId().equals(a[0]) && s.getCheckId().equals(a[1])).toList(),
                "saveAll", a -> { subjects.addAll((Collection<LpConflictCheckSubject>) a[0]); return a[0]; }));
        var hitRepo = fake(LpConflictCheckHitRepository.class, Map.of(
                "findByCheck", a -> hits.stream().filter(h -> h.getTenantId().equals(a[0]) && h.getCheckId().equals(a[1])).toList(),
                "saveAll", a -> { hits.addAll((Collection<LpConflictCheckHit>) a[0]); return a[0]; }));
        var settingRepo = fake(LpTrustSettingChangeRepository.class, Map.of(
                "save", a -> { settingRows.add((LpTrustSettingChange) a[0]); return a[0]; },
                "latest", a -> { List<LpTrustSettingChange> mine = new ArrayList<>(); for (var c : settingRows) if (c.getTenantId().equals(a[0])) mine.add(0, c); return mine; }));
        settings = new LpTrustSettingsService(settingRepo);
        service = new LpConflictCheckService(checkRepo, subjectRepo, hitRepo, partyRepo, clientSearch, clientRepo, matterRepo, settings);
        matterService = new LpMatterService(matterRepo, trustRepo, service, LpWallGuard.NONE, LpKycGate.NONE);
    }

    private LpClient client(TenantId t, String name, String idNo) {
        LpClient c = LpClient.create(t, name, null, null, "ENTITY", idNo, null);
        clients.put(c.getId(), c);
        return c;
    }

    private RunConflictCheckRequest.Party party(String name) { return new RunConflictCheckRequest.Party(name, null, "OPPOSING_PARTY"); }

    private LpConflictCheckResponse run(LpClient c, RunConflictCheckRequest.Party... ps) {
        return service.run(firm, new RunConflictCheckRequest(c.getId(), List.of(ps)), runner, "Clerk");
    }

    private CreateLpMatterRequest matterFor(LpClient c, UUID checkId) {
        return new CreateLpMatterRequest(c.getId(), UUID.randomUUID(), "M-1", "LITIGATION", "Dlamini v Nkosi", null, "HOURLY", null,
                LocalDate.now(), null, checkId);
    }

    private void backdate(UUID checkId, long days) throws Exception {
        Field f = LpConflictCheck.class.getDeclaredField("validUntil");
        f.setAccessible(true);
        f.set(checks.get(checkId), Instant.now().minus(days, ChronoUnit.DAYS));
    }

    // ── searching ────────────────────────────────────────────────────────────

    @Test
    void anEmptyFirmIsClearAtOnce() {
        var c = client(firm, "Dlamini Trading", null);
        var r = run(c, party("Nkosi Builders"));
        assertEquals("CLEAR", r.status());
        assertTrue(r.usable());
        assertTrue(r.hits().isEmpty());
        assertEquals(List.of("CLIENT", "OPPOSING_PARTY"), r.subjects().stream().map(LpConflictCheckResponse.Subject::role).toList());
    }

    @Test
    void anOpposingPartyWhoIsAnExistingClientIsFoundAndNeedsReview() {
        var c = client(firm, "Dlamini Trading", null);
        client(firm, "Nkosi Builders (Pty) Ltd", null);
        var r = run(c, party("Nkosi Builders"));
        assertEquals("PENDING_REVIEW", r.status());
        assertFalse(r.usable());
        assertEquals(1, r.hits().size());
        var h = r.hits().get(0);
        assertEquals("EXACT_NAME", h.matchKind());
        assertEquals("CLIENT", h.matchedKind());
        assertEquals("Current client of the firm", h.matchedDetail());
    }

    @Test
    void aFormerClientIsFoundToo() {
        var c = client(firm, "Dlamini Trading", null);
        var former = client(firm, "Nkosi Builders", null);
        former.deactivate();
        var r = run(c, party("Nkosi Builders"));
        assertEquals("Former client of the firm", r.hits().get(0).matchedDetail());
    }

    @Test
    void aNewClientWhoWasTheOtherSideOfAnEarlierMatterIsFound() {
        var old = client(firm, "Acme Holdings", null);
        var oldMatter = LpMatter.create(firm, old.getId(), UUID.randomUUID(), "M-2024-017", "LITIGATION", "Acme v Dlamini", null, "HOURLY", null, null, null);
        matters.put(oldMatter.getId(), oldMatter);
        parties.add(LpMatterParty.of(firm, oldMatter.getId(), old.getId(), "Thabo Dlamini", "8001015009087", "OPPOSING_PARTY"));
        var c = client(firm, "DLAMINI, Thabo", "800101 5009 087");
        var r = run(c);
        assertEquals("PENDING_REVIEW", r.status());
        assertEquals("EXACT_ID", r.hits().get(0).matchKind());
        assertEquals("MATTER_PARTY", r.hits().get(0).matchedKind());
        assertEquals("Opposing party on matter M-2024-017 for Acme Holdings", r.hits().get(0).matchedDetail());
    }

    @Test
    void theClientIsNeverInConflictWithThemselvesOrTheirOwnEarlierMatters() {
        var c = client(firm, "Dlamini Trading", null);
        var earlier = LpMatter.create(firm, c.getId(), UUID.randomUUID(), "M-1", "LITIGATION", "x", null, "HOURLY", null, null, null);
        matters.put(earlier.getId(), earlier);
        parties.add(LpMatterParty.of(firm, earlier.getId(), c.getId(), "Nkosi Builders", null, "OPPOSING_PARTY"));
        var r = run(c, party("Nkosi Builders"));
        assertEquals("CLEAR", r.status(), "the same opposing party on the same client's earlier matter is the same side, not a conflict");
    }

    @Test
    void anotherFirmsClientsAndPartiesAreNeverSearched() {
        var c = client(firm, "Dlamini Trading", null);
        client(otherFirm, "Nkosi Builders", null);
        parties.add(LpMatterParty.of(otherFirm, UUID.randomUUID(), UUID.randomUUID(), "Nkosi Builders", null, "OPPOSING_PARTY"));
        assertEquals("CLEAR", run(c, party("Nkosi Builders")).status());
    }

    @Test
    void aSimilarNameIsFlaggedAsSimilarNotExact() {
        var c = client(firm, "Dlamini Trading", null);
        client(firm, "Nkosi Builders and Civil Contractors", null);
        var r = run(c, party("Nkosi Builders Civil"));
        assertEquals("SIMILAR", r.hits().get(0).matchKind());
    }

    @Test
    void oneHitIsReportedOnceEvenIfSeveralWordsFindIt() {
        var c = client(firm, "Dlamini Trading", null);
        client(firm, "Nkosi Builders Civil", null);
        assertEquals(1, run(c, party("Nkosi Builders Civil")).hits().size());
    }

    @Test
    void blankPartyRowsAreIgnoredAndBadRolesRefused() {
        var c = client(firm, "Dlamini Trading", null);
        assertEquals(1, run(c, party(" "), new RunConflictCheckRequest.Party(null, null, null)).subjects().size());
        assertThrows(IllegalArgumentException.class, () -> run(c, new RunConflictCheckRequest.Party("X Y", null, "BOSS")));
        assertEquals(1, checks.size(), "the refused check was not saved");
    }

    @Test
    void anUnknownOrOtherFirmsClientCannotBeChecked() {
        var c = client(otherFirm, "Dlamini Trading", null);
        assertThrows(ResourceNotFoundException.class, () -> service.run(firm, new RunConflictCheckRequest(c.getId(), List.of()), runner, "Clerk"));
        assertThrows(ResourceNotFoundException.class, () -> service.run(firm, new RunConflictCheckRequest(UUID.randomUUID(), List.of()), runner, "Clerk"));
    }

    // ── reviewing ────────────────────────────────────────────────────────────

    private LpConflictCheckResponse pending() {
        var c = client(firm, "Dlamini Trading", null);
        client(firm, "Nkosi Builders", null);
        return run(c, party("Nkosi Builders"));
    }

    @Test
    void aDifferentPersonReviewsAndTheCheckBecomesUsable() {
        var p = pending();
        var d = service.decide(firm, p.id(), new DecideConflictCheckRequest("PROCEED_WITH_CONSENT", "Both clients gave written consent, on file"), reviewer, "Partner");
        assertEquals("PROCEED_WITH_CONSENT", d.status());
        assertTrue(d.usable());
        assertFalse(d.selfReviewed());
        assertNotNull(d.validUntil());
    }

    @Test
    void theRunnerCannotReviewTheirOwnCheckByDefault() {
        var p = pending();
        assertThrows(IllegalStateException.class, () -> service.decide(firm, p.id(),
                new DecideConflictCheckRequest("CLEARED_AFTER_REVIEW", "Different company with a similar name"), runner, "Clerk"));
        assertEquals("PENDING_REVIEW", checks.get(p.id()).getStatus());
    }

    @Test
    void whenTheFirmAllowsItTheRunnerMayReviewAndItIsFlagged() {
        settings.update(firm, true, "Sole practitioner with no second attorney", UUID.randomUUID(), "Owner");
        var p = pending();
        var d = service.decide(firm, p.id(), new DecideConflictCheckRequest("CLEARED_AFTER_REVIEW", "Different company with a similar name"), runner, "Clerk");
        assertTrue(d.selfReviewed());
        assertTrue(d.usable());
    }

    @Test
    void aReviewNeedsAKnownOutcomeAndAWrittenReason() {
        var p = pending();
        assertThrows(IllegalArgumentException.class, () -> service.decide(firm, p.id(), new DecideConflictCheckRequest("CLEAR", "Looks fine to me, honestly"), reviewer, "P"));
        assertThrows(IllegalArgumentException.class, () -> service.decide(firm, p.id(), new DecideConflictCheckRequest("DECLINED", "short"), reviewer, "P"));
        assertEquals("PENDING_REVIEW", checks.get(p.id()).getStatus());
    }

    @Test
    void aDecidedCheckCannotBeDecidedAgainAndAClearCheckNeedsNoReview() {
        var p = pending();
        service.decide(firm, p.id(), new DecideConflictCheckRequest("DECLINED", "We act for the other side already"), reviewer, "P");
        assertThrows(IllegalStateException.class, () -> service.decide(firm, p.id(),
                new DecideConflictCheckRequest("CLEARED_AFTER_REVIEW", "Changed my mind about this one"), reviewer, "P"));
        var clear = run(client(firm, "Solo Client", null));
        assertThrows(IllegalStateException.class, () -> service.decide(firm, clear.id(),
                new DecideConflictCheckRequest("DECLINED", "Not needed but trying anyway"), reviewer, "P"));
    }

    @Test
    void aDeclinedCheckIsNeverUsable() {
        var p = pending();
        var d = service.decide(firm, p.id(), new DecideConflictCheckRequest("DECLINED", "We act for the other side already"), reviewer, "P");
        assertFalse(d.usable());
        assertNull(d.validUntil());
    }

    @Test
    void anotherFirmCannotSeeOrDecideACheck() {
        var p = pending();
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherFirm, p.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.decide(otherFirm, p.id(),
                new DecideConflictCheckRequest("CLEARED_AFTER_REVIEW", "Not our check at all, sorry"), reviewer, "P"));
    }

    @Test
    void aSearchTooBroadToCompareInFullIsNeverClear() {
        // a word that matched as many rows as the limit allows means rows may have been missed
        var c = client(firm, "Dlamini Trading", null);
        for (int i = 0; i < LpConflictCheckService.CANDIDATE_LIMIT; i++) client(firm, "Trading Co " + i, null);
        var r = run(c, party("Trading Group"));
        assertTrue(r.truncated());
        assertEquals("PENDING_REVIEW", r.status());
    }

    @Test
    void initialsAreNotSearchedAsWordsSoTheyDoNotFloodTheSearch() {
        var c = client(firm, "Dlamini Trading", null);
        for (int i = 0; i < LpConflictCheckService.CANDIDATE_LIMIT; i++) client(firm, "Jones Corp " + i, null);
        var r = run(c, party("J R Smith"));
        assertFalse(r.truncated());
        assertEquals("CLEAR", r.status());
    }

    // ── the gate on opening a matter ─────────────────────────────────────────

    @Test
    void aMatterCannotBeOpenedWithoutAUsableCheckForThatClient() {
        var c = client(firm, "Dlamini Trading", null);
        assertThrows(ResourceNotFoundException.class, () -> matterService.createMatter(firm, matterFor(c, UUID.randomUUID())));
        assertTrue(matters.isEmpty());
    }

    @Test
    void aClearCheckOpensOneMatterAndRecordsTheOtherParties() {
        var c = client(firm, "Dlamini Trading", null);
        var r = run(c, party("Nkosi Builders"), new RunConflictCheckRequest.Party("Nkosi Builders", null, "OPPOSING_PARTY"),
                new RunConflictCheckRequest.Party("Sipho Zulu", "7001015009087", "WITNESS"));
        var m = matterService.createMatter(firm, matterFor(c, r.id()));
        assertEquals(m.id(), checks.get(r.id()).getMatterId());
        assertEquals(2, parties.size(), "the client is not a party, and a repeated name is recorded once");
        assertTrue(parties.stream().allMatch(p -> p.getMatterId().equals(m.id()) && p.getClientId().equals(c.getId())));
        assertEquals(Set.of("OPPOSING_PARTY", "WITNESS"), new HashSet<>(parties.stream().map(LpMatterParty::getRole).toList()));
    }

    @Test
    void aCheckOpensOnlyOneMatter() {
        var c = client(firm, "Dlamini Trading", null);
        var r = run(c);
        matterService.createMatter(firm, matterFor(c, r.id()));
        assertThrows(IllegalStateException.class, () -> matterService.createMatter(firm, matterFor(c, r.id())));
        assertEquals(1, matters.size());
    }

    @Test
    void aCheckForAnotherClientCannotBeUsed() {
        var c = client(firm, "Dlamini Trading", null);
        var other = client(firm, "Zulu Farms", null);
        var r = run(c);
        assertThrows(IllegalStateException.class, () -> matterService.createMatter(firm, matterFor(other, r.id())));
        assertTrue(matters.isEmpty());
    }

    @Test
    void aCheckWaitingForReviewOrDeclinedCannotBeUsed() {
        var p = pending();
        var c = clients.get(p.clientId());
        assertThrows(IllegalStateException.class, () -> matterService.createMatter(firm, matterFor(c, p.id())));
        service.decide(firm, p.id(), new DecideConflictCheckRequest("DECLINED", "We act for the other side already"), reviewer, "P");
        assertThrows(IllegalStateException.class, () -> matterService.createMatter(firm, matterFor(c, p.id())));
        assertTrue(matters.isEmpty());
    }

    @Test
    void aReviewedCheckCanBeUsed() {
        var p = pending();
        var c = clients.get(p.clientId());
        service.decide(firm, p.id(), new DecideConflictCheckRequest("PROCEED_WITH_CONSENT", "Both clients gave written consent, on file"), reviewer, "P");
        assertNotNull(matterService.createMatter(firm, matterFor(c, p.id())));
    }

    @Test
    void anExpiredCheckCannotBeUsed() throws Exception {
        var c = client(firm, "Dlamini Trading", null);
        var r = run(c);
        backdate(r.id(), 1);
        assertThrows(IllegalStateException.class, () -> matterService.createMatter(firm, matterFor(c, r.id())));
        assertTrue(matters.isEmpty());
    }

    @Test
    void anotherFirmsCheckCannotBeUsed() {
        var c = client(firm, "Dlamini Trading", null);
        var r = run(c);
        assertThrows(ResourceNotFoundException.class, () -> matterService.createMatter(otherFirm, matterFor(c, r.id())));
    }

    @Test
    void theNextCheckFindsThePartiesRecordedOnTheMatter() {
        var c = client(firm, "Dlamini Trading", null);
        matterService.createMatter(firm, matterFor(c, run(c, party("Nkosi Builders")).id()));
        var newClient = client(firm, "Nkosi Builders", null);
        var r = run(newClient);
        assertEquals("PENDING_REVIEW", r.status());
        assertEquals("Opposing party on matter M-1 for Dlamini Trading", r.hits().get(0).matchedDetail());
    }
}
