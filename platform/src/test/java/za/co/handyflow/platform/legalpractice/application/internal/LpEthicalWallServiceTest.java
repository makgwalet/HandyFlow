package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ethical walls against in-memory stand-ins for the repositories (no database, no Spring): who is turned away, what is recorded,
 * who may put a wall up or lift it, and that the matter, time, disbursement and key-date services actually ask. The migration's
 * constraints and triggers were run against PostgreSQL separately.
 */
class LpEthicalWallServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID admin = UUID.randomUUID();
    private final UUID barred = UUID.randomUUID();      // the person the walls bar
    private final UUID colleague = UUID.randomUUID();   // someone no wall names

    private final Map<UUID, LpEthicalWall> walls = new LinkedHashMap<>();
    private final List<LpWallDenial> denials = new ArrayList<>();
    private final Map<UUID, LpMatter> matters = new LinkedHashMap<>();
    private final Map<UUID, LpClient> clients = new LinkedHashMap<>();
    private final Map<UUID, LpTimeEntry> timeEntries = new LinkedHashMap<>();
    private final Map<UUID, LpDisbursement> disbursements = new LinkedHashMap<>();
    private final Map<UUID, LpMatterKeyDate> keyDates = new LinkedHashMap<>();
    private final List<String> saved = new ArrayList<>();

    private LpEthicalWallService service;
    private LpClient dlamini, nkosi;
    private LpMatter dlaminiMatter1, dlaminiMatter2, nkosiMatter;

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
        walls.clear(); denials.clear(); matters.clear(); clients.clear(); timeEntries.clear(); disbursements.clear(); keyDates.clear(); saved.clear();
        service = newService(null);
        dlamini = LpClient.create(firm, "Dlamini Trading", null, null, "COMPANY", null, null);
        nkosi = LpClient.create(firm, "Nkosi Builders", null, null, "COMPANY", null, null);
        clients.put(dlamini.getId(), dlamini); clients.put(nkosi.getId(), nkosi);
        dlaminiMatter1 = matter(dlamini, "M-1"); dlaminiMatter2 = matter(dlamini, "M-2"); nkosiMatter = matter(nkosi, "M-3");
        signIn(barred);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private void signIn(UUID user) { TenantContext.setUserId(user.toString()); TenantContext.setUserName("User " + user.toString().substring(0, 4)); }

    private LpMatter matter(LpClient c, String number) {
        LpMatter m = LpMatter.create(firm, c.getId(), UUID.randomUUID(), number, "LITIGATION", "Matter " + number, null, "HOURLY", null, LocalDate.of(2026, 1, 5), null);
        matters.put(m.getId(), m);
        return m;
    }

    private LpEthicalWallService newService(LpWallDenialLogger logger) {
        var wallRepo = fake(LpEthicalWallRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(walls.get((UUID) a[1])).filter(w -> w.getTenantId().equals(a[0])),
                "list", a -> new PageImpl<>(walls.values().stream().filter(w -> w.getTenantId().equals(a[0]) && (!(Boolean) a[1] || w.active())).toList()),
                "findInForceForUser", a -> walls.values().stream().filter(w -> w.getTenantId().equals(a[0]) && w.getUserId().equals(a[1]) && w.active()).toList(),
                "countInForceOnMatter", a -> walls.values().stream().filter(w -> w.getTenantId().equals(a[0]) && w.getUserId().equals(a[1]) && w.active() && a[2].equals(w.getMatterId())).count(),
                "countInForceOnClient", a -> walls.values().stream().filter(w -> w.getTenantId().equals(a[0]) && w.getUserId().equals(a[1]) && w.active() && a[2].equals(w.getClientId())).count(),
                "save", a -> { LpEthicalWall w = (LpEthicalWall) a[0]; walls.put(w.getId(), w); return w; }));
        var denialRepo = fake(LpWallDenialRepository.class, Map.of(
                "save", a -> { denials.add((LpWallDenial) a[0]); return a[0]; },
                "recent", a -> new PageImpl<>(denials.stream().sorted(Comparator.comparing(LpWallDenial::getDeniedAt).reversed()).toList())));
        var matterRepo = matterRepo();
        var clientRepo = fake(LpClientRepository.class, Map.of("findActiveById", a -> Optional.ofNullable(clients.get((UUID) a[1])).filter(c -> c.getTenantId().equals(a[0]))));
        return new LpEthicalWallService(wallRepo, denialRepo, matterRepo, clientRepo, logger != null ? logger : new LpWallDenialLogger(denialRepo));
    }

    private LpMatterRepository matterRepo() {
        return fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(matters.get((UUID) a[1])).filter(m -> m.getTenantId().equals(a[0])),
                "save", a -> { saved.add("matter"); return a[0]; },
                "findAllActiveForFirmExcluding", a -> new PageImpl<>(matters.values().stream().filter(m -> m.getTenantId().equals(a[0])
                        && !((List<?>) a[1]).contains(m.getId()) && !((List<?>) a[2]).contains(m.getClientId())).toList()),
                "findAllActiveForClientExcluding", a -> new PageImpl<>(matters.values().stream().filter(m -> m.getTenantId().equals(a[0])
                        && m.getClientId().equals(a[1]) && !((List<?>) a[2]).contains(m.getId())).toList())));
    }

    private CreateWallRequest onMatter(LpMatter m, UUID user) { return new CreateWallRequest("MATTER", m.getId(), null, user, "Barred Person", "Acted for the other side before joining"); }
    private CreateWallRequest onClient(LpClient c, UUID user) { return new CreateWallRequest("CLIENT", null, c.getId(), user, "Barred Person", "Related to the client's director"); }

    private void assertDenied(Runnable r) { assertThrows(AccessDeniedException.class, r::run); }

    // ---- who is barred -----------------------------------------------------------------------------------------------

    @Test
    void aMatterWallBarsThatPersonFromThatMatterOnly() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        assertDenied(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, dlaminiMatter2.getId()));
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, nkosiMatter.getId()));
    }

    @Test
    void aClientWallBarsEveryMatterOfThatClientAndTheClient() {
        service.create(firm, onClient(dlamini, barred), admin, "Admin");
        assertDenied(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
        assertDenied(() -> service.requireMatterAccess(firm, dlaminiMatter2.getId()));
        assertDenied(() -> service.requireClientAccess(firm, dlamini.getId()));
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, nkosiMatter.getId()));
        assertDoesNotThrow(() -> service.requireClientAccess(firm, nkosi.getId()));
    }

    @Test
    void aMatterWallDoesNotBarTheClientRecordOrOtherMattersOfTheClient() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        assertDoesNotThrow(() -> service.requireClientAccess(firm, dlamini.getId()));
        assertDoesNotThrow(() -> service.requireAccess(firm, dlaminiMatter2));
    }

    @Test
    void aColleagueTheWallDoesNotNameIsNotBarred() {
        service.create(firm, onClient(dlamini, barred), admin, "Admin");
        signIn(colleague);
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
        assertDoesNotThrow(() -> service.requireClientAccess(firm, dlamini.getId()));
    }

    @Test
    void aWallAppliesToTheAdministratorWhoPutItUpWhenTheyAreTheOneBarred() {
        signIn(admin);
        service.create(firm, onMatter(dlaminiMatter1, admin), admin, "Admin");
        assertDenied(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
    }

    @Test
    void anotherFirmsWallDoesNotApplyHere() {
        LpEthicalWall foreign = LpEthicalWall.onMatter(otherFirm, dlaminiMatter1.getId(), barred, "X", "Some long enough reason", admin, "A", Instant.now());
        walls.put(foreign.getId(), foreign);
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
        assertEquals(List.of(LpWallGuard.Barred.NONE_ID), service.barred(firm).matterIds());
    }

    @Test
    void aLiftedWallNoLongerBars() {
        WallResponse w = service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        service.lift(firm, w.id(), "Conflict resolved, the other side has consented", admin, "Admin");
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
        assertEquals(List.of(LpWallGuard.Barred.NONE_ID), service.barred(firm).matterIds());
    }

    @Test
    void noSignedInUserMeansABackgroundJobWhichIsNotBarred() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        TenantContext.clear();
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
    }

    @Test
    void aMatterThatDoesNotExistPassesSoTheCallerReportsNotFound() {
        service.create(firm, onClient(dlamini, barred), admin, "Admin");
        assertDoesNotThrow(() -> service.requireMatterAccess(firm, UUID.randomUUID()));
    }

    @Test
    void theRefusalDoesNotSayWhyOrWhoOrWhichWall() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        AccessDeniedException e = assertThrows(AccessDeniedException.class, () -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
        assertEquals("You are not permitted to access this matter", e.getMessage());
    }

    // ---- the record of refusals --------------------------------------------------------------------------------------

    @Test
    void everyRefusalIsRecordedWithWhoWhichMatterAndWhichWall() {
        WallResponse w = service.create(firm, onClient(dlamini, barred), admin, "Admin");
        assertDenied(() -> service.requireMatterAccess(firm, dlaminiMatter2.getId()));
        assertEquals(1, denials.size());
        LpWallDenial d = denials.get(0);
        assertEquals(barred, d.getUserId());
        assertEquals(dlaminiMatter2.getId(), d.getMatterId());
        assertEquals(dlamini.getId(), d.getClientId());
        assertEquals(w.id(), d.getWallId());
        assertEquals(firm, d.getTenantId());
        assertNotNull(d.getDeniedAt());
    }

    @Test
    void aClientLevelRefusalIsRecordedToo() {
        service.create(firm, onClient(dlamini, barred), admin, "Admin");
        assertDenied(() -> service.requireClientAccess(firm, dlamini.getId()));
        assertEquals(1, denials.size());
        assertEquals(dlamini.getId(), denials.get(0).getClientId());
    }

    @Test
    void successfulAccessIsNotRecorded() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        service.requireMatterAccess(firm, nkosiMatter.getId());
        assertTrue(denials.isEmpty());
    }

    @Test
    void theRefusalStandsEvenIfTheRecordCannotBeWritten() {
        var denialRepo = fake(LpWallDenialRepository.class, Map.of());
        LpEthicalWallService broken = newService(new LpWallDenialLogger(denialRepo) {
            @Override public void record(TenantId t, UUID u, String n, UUID m, UUID c, UUID w) { throw new IllegalStateException("database down"); }
        });
        // the walls live in the shared maps, so put one up through the working service
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        assertThrows(AccessDeniedException.class, () -> broken.requireMatterAccess(firm, dlaminiMatter1.getId()));
    }

    @Test
    void theDenialLogListsMostRecentFirstWithTheMatterNamed() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "Admin");
        assertDenied(() -> service.requireMatterAccess(firm, dlaminiMatter1.getId()));
        var page = service.denials(firm, PageRequest.of(0, 20));
        assertEquals(1, page.getContent().size());
        assertEquals("M-1 Matter M-1", page.getContent().get(0).matterLabel());
    }

    // ---- putting a wall up -------------------------------------------------------------------------------------------

    @Test
    void aWallNeedsAScopeATargetAPersonAndAReason() {
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, new CreateWallRequest("FIRM", null, null, barred, "B", "A long enough reason"), admin, "A"));
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, new CreateWallRequest("MATTER", null, null, barred, "B", "A long enough reason"), admin, "A"));
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, new CreateWallRequest("CLIENT", null, null, barred, "B", "A long enough reason"), admin, "A"));
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, new CreateWallRequest("MATTER", dlaminiMatter1.getId(), null, null, "B", "A long enough reason"), admin, "A"));
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, new CreateWallRequest("MATTER", dlaminiMatter1.getId(), null, barred, "B", "short"), admin, "A"));
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, new CreateWallRequest("MATTER", dlaminiMatter1.getId(), null, barred, "B", "         "), admin, "A"));
        assertTrue(walls.isEmpty());
    }

    @Test
    void aWallOnAMatterOrClientThatIsNotThisFirmsIsRefused() {
        LpMatter foreign = LpMatter.create(otherFirm, UUID.randomUUID(), UUID.randomUUID(), "X-1", "LITIGATION", "Foreign", null, "HOURLY", null, LocalDate.now(), null);
        matters.put(foreign.getId(), foreign);
        assertThrows(ResourceNotFoundException.class, () -> service.create(firm, onMatter(foreign, barred), admin, "A"));
        assertThrows(ResourceNotFoundException.class, () -> service.create(firm, new CreateWallRequest("CLIENT", null, UUID.randomUUID(), barred, "B", "A long enough reason"), admin, "A"));
        assertTrue(walls.isEmpty());
    }

    @Test
    void theSamePersonCannotBeWalledOffTheSameThingTwiceButCanBeAfterItIsLifted() {
        WallResponse first = service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        assertThrows(IllegalStateException.class, () -> service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A"));
        service.create(firm, onClient(dlamini, barred), admin, "A");
        assertThrows(IllegalStateException.class, () -> service.create(firm, onClient(dlamini, barred), admin, "A"));
        service.lift(firm, first.id(), "Consent given by the other party", admin, "A");
        assertDoesNotThrow(() -> service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A"));
    }

    @Test
    void theWallRecordsWhoPutItUpAndWhy() {
        WallResponse w = service.create(firm, onClient(dlamini, barred), admin, "Admin Person");
        assertEquals("Admin Person", w.createdByName());
        assertEquals("Related to the client's director", w.reason());
        assertEquals("Dlamini Trading", w.targetLabel());
        assertTrue(w.active());
    }

    // ---- lifting a wall ----------------------------------------------------------------------------------------------

    @Test
    void theBarredPersonCannotLiftTheirOwnWall() {
        WallResponse w = service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        assertThrows(IllegalStateException.class, () -> service.lift(firm, w.id(), "I would like to work on it again", barred, "Barred Person"));
        assertTrue(walls.get(w.id()).active());
    }

    @Test
    void someoneElseLiftsItWithAReasonAndItIsRecorded() {
        WallResponse w = service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        WallResponse lifted = service.lift(firm, w.id(), "The other side has consented in writing", admin, "Admin Person");
        assertFalse(lifted.active());
        assertEquals("Admin Person", lifted.liftedByName());
        assertEquals("The other side has consented in writing", lifted.liftReason());
        assertNotNull(lifted.liftedAt());
    }

    @Test
    void liftingNeedsARealReasonAndHappensOnce() {
        WallResponse w = service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        assertThrows(IllegalArgumentException.class, () -> service.lift(firm, w.id(), "ok", admin, "A"));
        assertTrue(walls.get(w.id()).active());
        service.lift(firm, w.id(), "The other side has consented", admin, "A");
        assertThrows(IllegalStateException.class, () -> service.lift(firm, w.id(), "Trying again for no reason", admin, "A"));
    }

    @Test
    void aWallOfAnotherFirmCannotBeLiftedFromHere() {
        LpEthicalWall foreign = LpEthicalWall.onMatter(otherFirm, UUID.randomUUID(), barred, "X", "Some long enough reason", admin, "A", Instant.now());
        walls.put(foreign.getId(), foreign);
        assertThrows(ResourceNotFoundException.class, () -> service.lift(firm, foreign.getId(), "Trying to lift it from the wrong firm", admin, "A"));
    }

    @Test
    void theListShowsOnlyThisFirmsAndCanHideLiftedWalls() {
        WallResponse a = service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        service.create(firm, onClient(nkosi, barred), admin, "A");
        service.lift(firm, a.id(), "The other side has consented", admin, "A");
        LpEthicalWall foreign = LpEthicalWall.onMatter(otherFirm, UUID.randomUUID(), barred, "X", "Some long enough reason", admin, "A", Instant.now());
        walls.put(foreign.getId(), foreign);
        assertEquals(1, service.list(firm, true, PageRequest.of(0, 10)).getContent().size());
        assertEquals(2, service.list(firm, false, PageRequest.of(0, 10)).getContent().size());
    }

    // ---- the services actually ask ----------------------------------------------------------------------------------------

    private LpMatterService matterService() { return new LpMatterService(matterRepo(), null, null, service, LpKycGate.NONE); }

    @Test
    void theMatterServiceRefusesTheBarredPersonOnReadAndEveryChange() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        LpMatterService ms = matterService();
        UUID id = dlaminiMatter1.getId();
        assertDenied(() -> ms.getMatter(firm, id));
        assertDenied(() -> ms.updateMatter(firm, id, new UpdateLpMatterRequest(UUID.randomUUID(), "New name", null, "HOURLY", null, null)));
        assertDenied(() -> ms.putOnHold(firm, id));
        assertDenied(() -> ms.reopen(firm, id));
        assertDenied(() -> ms.close(firm, id, new CloseMatterRequest(LocalDate.now())));
        assertDenied(() -> ms.archive(firm, id));
        assertTrue(saved.isEmpty(), "nothing was saved");
        assertDoesNotThrow(() -> ms.getMatter(firm, dlaminiMatter2.getId()));
    }

    @Test
    void theMatterListsLeaveOutWhatThePersonIsBarredFrom() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        service.create(firm, onClient(nkosi, barred), admin, "A");
        LpMatterService ms = matterService();
        var firmList = ms.listForFirm(firm, PageRequest.of(0, 50)).getContent();
        assertEquals(List.of(dlaminiMatter2.getId()), firmList.stream().map(LpMatterResponse::id).toList());
        var dlaminiList = ms.listForClient(firm, dlamini.getId(), PageRequest.of(0, 50)).getContent();
        assertEquals(List.of(dlaminiMatter2.getId()), dlaminiList.stream().map(LpMatterResponse::id).toList());
        assertDenied(() -> ms.listForClient(firm, nkosi.getId(), PageRequest.of(0, 50)));
        signIn(colleague);
        assertEquals(3, ms.listForFirm(firm, PageRequest.of(0, 50)).getContent().size());
    }

    private LpTimeEntryService timeService() {
        var repo = fake(LpTimeEntryRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(timeEntries.get((UUID) a[1])),
                "findAllForMatter", a -> new PageImpl<>(timeEntries.values().stream().filter(t -> t.getMatterId().equals(a[1])).toList()),
                "findUnbilledByMatter", a -> timeEntries.values().stream().filter(t -> t.getMatterId().equals(a[1])).toList(),
                "save", a -> { saved.add("time"); return a[0]; }));
        var attorneys = fake(LpAttorneyRepository.class, Map.of());
        return new LpTimeEntryService(repo, matterRepo(), attorneys, service);
    }

    @Test
    void theTimeServiceRefusesTheBarredPersonOnListLogAndChange() {
        service.create(firm, onClient(dlamini, barred), admin, "A");
        LpTimeEntry existing = LpTimeEntry.create(firm, dlaminiMatter1.getId(), UUID.randomUUID(), LocalDate.of(2026, 10, 1), new BigDecimal("1.5"), new BigDecimal("1500"), "Drafting", true);
        timeEntries.put(existing.getId(), existing);
        LpTimeEntryService ts = timeService();
        UUID m = dlaminiMatter1.getId();
        assertDenied(() -> ts.listForMatter(firm, m, PageRequest.of(0, 10)));
        assertDenied(() -> ts.listUnbilledForMatter(firm, m));
        assertDenied(() -> ts.createTimeEntry(firm, m, new CreateLpTimeEntryRequest(UUID.randomUUID(), LocalDate.now(), BigDecimal.ONE, new BigDecimal("1000"), "Call", true)));
        assertDenied(() -> ts.updateTimeEntry(firm, existing.getId(), new UpdateLpTimeEntryRequest(LocalDate.now(), BigDecimal.ONE, new BigDecimal("1000"), "Edited")));
        assertDenied(() -> ts.writeOff(firm, existing.getId()));
        assertTrue(saved.isEmpty());
        signIn(colleague);
        assertDoesNotThrow(() -> ts.listForMatter(firm, m, PageRequest.of(0, 10)));
        assertDoesNotThrow(() -> ts.writeOff(firm, existing.getId()));
    }

    @Test
    void theDisbursementServiceRefusesTheBarredPerson() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        LpDisbursement d = LpDisbursement.create(firm, dlaminiMatter1.getId(), LocalDate.of(2026, 10, 2), "Sheriff fees", new BigDecimal("350"), false);
        disbursements.put(d.getId(), d);
        var repo = fake(LpDisbursementRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(disbursements.get((UUID) a[1])),
                "findAllForMatter", a -> new PageImpl<>(List.of(d)),
                "findUnbilledByMatter", a -> List.of(d),
                "save", a -> { saved.add("disb"); return a[0]; }));
        LpDisbursementService ds = new LpDisbursementService(repo, matterRepo(), service);
        UUID m = dlaminiMatter1.getId();
        assertDenied(() -> ds.listForMatter(firm, m, PageRequest.of(0, 10)));
        assertDenied(() -> ds.listUnbilledForMatter(firm, m));
        assertDenied(() -> ds.createDisbursement(firm, m, new CreateLpDisbursementRequest(LocalDate.now(), "Copies", BigDecimal.TEN, false)));
        assertDenied(() -> ds.updateDisbursement(firm, d.getId(), new UpdateLpDisbursementRequest(LocalDate.now(), "Edited", BigDecimal.TEN)));
        assertDenied(() -> ds.writeOff(firm, d.getId()));
        assertTrue(saved.isEmpty());
        signIn(colleague);
        assertDoesNotThrow(() -> ds.listUnbilledForMatter(firm, m));
    }

    @Test
    void theKeyDateServiceRefusesTheBarredPerson() {
        service.create(firm, onMatter(dlaminiMatter1, barred), admin, "A");
        LpMatterKeyDate k = LpMatterKeyDate.create(firm, dlaminiMatter1.getId(), "COURT_DATE", LocalDate.of(2026, 12, 1), "Trial", null);
        keyDates.put(k.getId(), k);
        var repo = fake(LpMatterKeyDateRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(keyDates.get((UUID) a[1])),
                "findAllForMatter", a -> List.of(k),
                "save", a -> { saved.add("key"); return a[0]; }));
        LpMatterKeyDateService ks = new LpMatterKeyDateService(repo, service);
        UUID m = dlaminiMatter1.getId();
        assertDenied(() -> ks.listForMatter(firm, m));
        assertDenied(() -> ks.createKeyDate(firm, m, new CreateLpMatterKeyDateRequest("COURT_DATE", LocalDate.now(), "Hearing", null)));
        assertDenied(() -> ks.complete(firm, k.getId()));
        assertDenied(() -> ks.acknowledge(firm, k.getId()));
        assertDenied(() -> ks.markMissed(firm, k.getId()));
        assertTrue(saved.isEmpty());
        signIn(colleague);
        assertDoesNotThrow(() -> ks.listForMatter(firm, m));
    }
}
