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
 * The places a wall must also hold: trust, payment requests, invoices, the client record, the firm calendar and deadlines. The wall
 * rules themselves are in {@code LpEthicalWallServiceTest}; this class checks that each of these services asks, refuses a barred person
 * without saving anything, and leaves walled matters and clients out of its lists. In-memory stand-ins, no database, no Spring.
 */
class LpWallCoverageTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final UUID admin = UUID.randomUUID();
    private final UUID barred = UUID.randomUUID();
    private final UUID colleague = UUID.randomUUID();

    private final Map<UUID, LpEthicalWall> walls = new LinkedHashMap<>();
    private final List<LpWallDenial> denials = new ArrayList<>();
    private final Map<UUID, LpClient> clients = new LinkedHashMap<>();
    private final Map<UUID, LpMatter> matters = new LinkedHashMap<>();
    private final Map<UUID, LpInvoice> invoices = new LinkedHashMap<>();
    private final Map<UUID, LpTrustPaymentRequest> requests = new LinkedHashMap<>();
    private final Map<UUID, LpMatterKeyDate> keyDates = new LinkedHashMap<>();
    private final List<LpTrustTransaction> ledger = new ArrayList<>();
    private final List<String> saved = new ArrayList<>();
    private final List<String> asked = new ArrayList<>();  // what each list query was told to leave out

    private LpEthicalWallService guard;
    private LpClient dlamini, nkosi;
    private LpMatter d1, d2, n1;
    private LpTrustTransactionService trust;
    private LpTrustPaymentRequestService requestService;
    private LpBillingService billing;
    private LpClientService clientService;
    private LpMatterKeyDateService keyDateService;
    private LpDeadlineService deadlineService;

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

    private static <T> Optional<T> ownedBy(Map<UUID, T> store, Object tenant, Object id, Function<T, TenantId> tenantOf) {
        T t = store.get((UUID) id);
        return t != null && tenantOf.apply(t).equals(tenant) ? Optional.of(t) : Optional.empty();
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        walls.clear(); denials.clear(); clients.clear(); matters.clear(); invoices.clear(); requests.clear(); keyDates.clear(); ledger.clear(); saved.clear(); asked.clear();
        var wallRepo = fake(LpEthicalWallRepository.class, Map.of(
                "findActiveById", a -> ownedBy(walls, a[0], a[1], LpEthicalWall::getTenantId),
                "findInForceForUser", a -> walls.values().stream().filter(w -> w.getTenantId().equals(a[0]) && w.getUserId().equals(a[1]) && w.active()).toList(),
                "countInForceOnMatter", a -> 0L, "countInForceOnClient", a -> 0L,
                "save", a -> { LpEthicalWall w = (LpEthicalWall) a[0]; walls.put(w.getId(), w); return w; }));
        var denialRepo = fake(LpWallDenialRepository.class, Map.of("save", a -> { denials.add((LpWallDenial) a[0]); return a[0]; }));
        var clientRepo = fake(LpClientRepository.class, Map.of(
                "findActiveById", a -> ownedBy(clients, a[0], a[1], LpClient::getTenantId),
                "findAllActiveExcluding", a -> { asked.add("clients:" + a[1]); return new PageImpl<>(clients.values().stream().filter(c -> !((List<UUID>) a[1]).contains(c.getId())).toList()); },
                "save", a -> { saved.add("client"); return a[0]; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of("findActiveById", a -> ownedBy(matters, a[0], a[1], LpMatter::getTenantId), "save", a -> { saved.add("matter"); return a[0]; }));
        guard = new LpEthicalWallService(wallRepo, denialRepo, matterRepo, clientRepo, new LpWallDenialLogger(denialRepo));

        var invoiceRepo = fake(LpInvoiceRepository.class, Map.of(
                "findActiveById", a -> ownedBy(invoices, a[0], a[1], LpInvoice::getTenantId),
                "save", a -> { saved.add("invoice"); return a[0]; },
                "findAllForFirmExcluding", a -> new PageImpl<>(invoices.values().stream().filter(i -> !((List<UUID>) a[2]).contains(i.getClientId())
                        && (i.getMatterId() == null || !((List<UUID>) a[1]).contains(i.getMatterId()))).toList()),
                "findAllForClientExcluding", a -> new PageImpl<>(invoices.values().stream().filter(i -> i.getClientId().equals(a[1])
                        && (i.getMatterId() == null || !((List<UUID>) a[2]).contains(i.getMatterId()))).toList())));
        var trustRepo = fake(LpTrustTransactionRepository.class, Map.of(
                "save", a -> { saved.add("trust"); ledger.add((LpTrustTransaction) a[0]); return a[0]; },
                "findByIdempotencyKey", a -> Optional.empty(),
                "findByClientExcluding", a -> new PageImpl<>(ledger.stream().filter(t -> t.getClientId().equals(a[1])
                        && (t.getMatterId() == null || !((List<UUID>) a[2]).contains(t.getMatterId()))).toList())));
        LpAccountingPoster poster = new LpAccountingPoster(null);
        trust = new LpTrustTransactionService(trustRepo, clientRepo, invoiceRepo, matterRepo, poster, guard, LpKycGate.NONE);
        var requestRepo = fake(LpTrustPaymentRequestRepository.class, Map.of(
                "findActiveById", a -> ownedBy(requests, a[0], a[1], LpTrustPaymentRequest::getTenantId),
                "findByIdempotencyKey", a -> Optional.empty(),
                "save", a -> { LpTrustPaymentRequest r = (LpTrustPaymentRequest) a[0]; saved.add("request"); requests.put(r.getId(), r); return r; },
                "searchExcluding", a -> new PageImpl<>(requests.values().stream().filter(r -> !((List<UUID>) a[3]).contains(r.getClientId())
                        && (r.getMatterId() == null || !((List<UUID>) a[2]).contains(r.getMatterId()))).toList())));
        var settingRepo = fake(LpTrustSettingChangeRepository.class, Map.of("latest", a -> new ArrayList<LpTrustSettingChange>()));
        requestService = new LpTrustPaymentRequestService(requestRepo, clientRepo, trust, new LpTrustSettingsService(settingRepo), guard);
        billing = new LpBillingService(invoiceRepo, null, null, clientRepo, poster, null, guard);
        clientService = new LpClientService(clientRepo, guard);
        var keyDateRepo = fake(LpMatterKeyDateRepository.class, Map.of(
                "findAllForMatter", a -> List.of(),
                "findInRangeWithMatterExcluding", a -> keyDates.values().stream()
                        .map(k -> new Object[]{k, matters.get(k.getMatterId())})
                        .filter(row -> !((List<UUID>) a[3]).contains(((LpMatter) row[1]).getId()) && !((List<UUID>) a[4]).contains(((LpMatter) row[1]).getClientId())).toList()));
        keyDateService = new LpMatterKeyDateService(keyDateRepo, guard);
        deadlineService = new LpDeadlineService(null, null, fake(LpDeadlineComputationRepository.class, Map.of("findAllForMatter", a -> List.of())), matterRepo, keyDateRepo, guard);

        dlamini = client("Dlamini Trading"); nkosi = client("Nkosi Builders");
        d1 = matter(dlamini, "M-1"); d2 = matter(dlamini, "M-2"); n1 = matter(nkosi, "M-3");
        signIn(barred);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private void signIn(UUID u) { TenantContext.setUserId(u.toString()); TenantContext.setUserName("User"); }

    private LpClient client(String name) {
        LpClient c = LpClient.create(firm, name, null, null, "COMPANY", null, null);
        c.increaseTrustBalance(bd("1000.00"));
        clients.put(c.getId(), c);
        return c;
    }

    private LpMatter matter(LpClient c, String number) {
        LpMatter m = LpMatter.create(firm, c.getId(), UUID.randomUUID(), number, "LITIGATION", "Matter " + number, null, "HOURLY", null, LocalDate.of(2026, 1, 5), null);
        matters.put(m.getId(), m);
        return m;
    }

    private LpInvoice invoice(LpClient c, LpMatter m, String number) {
        LpInvoice i = LpInvoice.create(firm, c.getId(), m == null ? null : m.getId(), number, null, LocalDate.now(), null, bd("100"), null, bd("0"));
        invoices.put(i.getId(), i);
        return i;
    }

    private LpMatterKeyDate keyDate(LpMatter m) {
        LpMatterKeyDate k = LpMatterKeyDate.create(firm, m.getId(), "COURT_DATE", LocalDate.of(2026, 12, 1), "Trial", null);
        keyDates.put(k.getId(), k);
        return k;
    }

    private void wallMatter(LpMatter m) { LpEthicalWall w = LpEthicalWall.onMatter(firm, m.getId(), barred, "B", "Acted for the other side", admin, "A", Instant.now()); walls.put(w.getId(), w); }
    private void wallClient(LpClient c) { LpEthicalWall w = LpEthicalWall.onClient(firm, c.getId(), barred, "B", "Related to the director", admin, "A", Instant.now()); walls.put(w.getId(), w); }
    private void denied(Runnable r) { assertThrows(AccessDeniedException.class, r::run); }
    private RecordTrustReceiptRequest receiptOn(UUID matterId) { return new RecordTrustReceiptRequest(matterId, bd("100"), null, null, null, null); }

    // ---- trust ---------------------------------------------------------------------------------------------------

    @Test
    void aMatterWallStopsTrustMoneyOnThatMatterButNotOnTheClientsOtherMattersOrWithNoMatter() {
        wallMatter(d1);
        denied(() -> trust.recordReceipt(firm, dlamini.getId(), receiptOn(d1.getId()), barred, "B"));
        assertTrue(ledger.isEmpty());
        assertDoesNotThrow(() -> trust.recordReceipt(firm, dlamini.getId(), receiptOn(d2.getId()), barred, "B"));
        assertDoesNotThrow(() -> trust.recordReceipt(firm, dlamini.getId(), receiptOn(null), barred, "B"));
        assertEquals(2, ledger.size());
    }

    @Test
    void aClientWallStopsAllTrustActivityForThatClient() {
        wallClient(dlamini);
        denied(() -> trust.recordReceipt(firm, dlamini.getId(), receiptOn(null), barred, "B"));
        denied(() -> trust.recordReceipt(firm, dlamini.getId(), receiptOn(d2.getId()), barred, "B"));
        denied(() -> trust.listByClient(firm, dlamini.getId(), PageRequest.of(0, 20)));
        denied(() -> trust.preflight(firm, dlamini.getId(), "DISBURSEMENT_PAYMENT", null, null, bd("10")));
        assertTrue(ledger.isEmpty());
        assertDoesNotThrow(() -> trust.recordReceipt(firm, nkosi.getId(), receiptOn(null), barred, "B"));
    }

    @Test
    void theTrustLedgerListLeavesOutRowsOnAWalledMatter() {
        signIn(colleague);
        trust.recordReceipt(firm, dlamini.getId(), receiptOn(d1.getId()), colleague, "C");
        trust.recordReceipt(firm, dlamini.getId(), receiptOn(d2.getId()), colleague, "C");
        trust.recordReceipt(firm, dlamini.getId(), receiptOn(null), colleague, "C");
        assertEquals(3, trust.listByClient(firm, dlamini.getId(), PageRequest.of(0, 20)).getContent().size());
        signIn(barred);
        wallMatter(d1);
        var rows = trust.listByClient(firm, dlamini.getId(), PageRequest.of(0, 20)).getContent();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().noneMatch(r -> d1.getId().equals(r.matterId())));
    }

    // ---- payment requests ----------------------------------------------------------------------------------------

    private CreateTrustPaymentRequest paymentOn(UUID matterId) { return new CreateTrustPaymentRequest("DISBURSEMENT_PAYMENT", matterId, null, bd("10"), null, "Sheriff", "R1", null, null); }

    @Test
    void aBarredPersonCannotRequestApproveRejectOrCancelAPaymentOnAWalledMatter() {
        signIn(colleague);
        var req = requestService.create(firm, dlamini.getId(), paymentOn(d1.getId()), colleague, "C");
        signIn(barred);
        wallMatter(d1);
        denied(() -> requestService.create(firm, dlamini.getId(), paymentOn(d1.getId()), barred, "B"));
        denied(() -> requestService.approve(firm, req.id(), barred, "B", null));
        denied(() -> requestService.reject(firm, req.id(), barred, "B", "Not convinced about this payment"));
        denied(() -> requestService.cancel(firm, req.id(), barred, "B"));
        assertEquals("PENDING", requests.get(req.id()).getStatus(), "the request was not touched");
        assertEquals(1, requests.size());
    }

    @Test
    void theRequestListLeavesOutRequestsOnWalledMattersAndClients() {
        signIn(colleague);
        requestService.create(firm, dlamini.getId(), paymentOn(d1.getId()), colleague, "C");
        requestService.create(firm, dlamini.getId(), paymentOn(d2.getId()), colleague, "C");
        requestService.create(firm, nkosi.getId(), paymentOn(null), colleague, "C");
        signIn(barred);
        wallMatter(d1); wallClient(nkosi);
        var rows = requestService.list(firm, null, PageRequest.of(0, 20)).getContent();
        assertEquals(1, rows.size());
        assertEquals(d2.getId(), rows.get(0).matterId());
    }

    // ---- invoices ------------------------------------------------------------------------------------------------

    @Test
    void aBarredPersonCannotReadSendPayWriteOffOrGenerateAnInvoiceOnAWalledMatter() {
        LpInvoice onD1 = invoice(dlamini, d1, "INV-1");
        wallMatter(d1);
        denied(() -> billing.getInvoice(firm, onD1.getId()));
        denied(() -> billing.markSent(firm, onD1.getId()));
        denied(() -> billing.generateInvoice(firm, new GenerateInvoiceRequest(dlamini.getId(), d1.getId(), null, null, bd("500"), null, "Fixed fee", null)));
        assertTrue(saved.isEmpty());
        assertEquals("INV-1", onD1.getInvoiceNumber());
    }

    @Test
    void aMatterWallLeavesTheClientsOtherAndMatterlessInvoicesAlone_AClientWallDoesNot() {
        LpInvoice onD2 = invoice(dlamini, d2, "INV-2"), noMatter = invoice(dlamini, null, "INV-3");
        wallMatter(d1);
        assertDoesNotThrow(() -> billing.getInvoice(firm, onD2.getId()));
        assertDoesNotThrow(() -> billing.getInvoice(firm, noMatter.getId()));
        wallClient(dlamini);
        denied(() -> billing.getInvoice(firm, noMatter.getId()));
        denied(() -> billing.getInvoice(firm, onD2.getId()));
    }

    @Test
    void theInvoiceListsLeaveOutWhatThePersonIsBarredFrom() {
        invoice(dlamini, d1, "INV-1"); invoice(dlamini, d2, "INV-2"); invoice(dlamini, null, "INV-3"); invoice(nkosi, n1, "INV-4");
        wallMatter(d1);
        assertEquals(3, billing.listForFirm(firm, PageRequest.of(0, 20)).getContent().size());
        assertEquals(2, billing.listForClient(firm, dlamini.getId(), PageRequest.of(0, 20)).getContent().size());
        wallClient(nkosi);
        assertEquals(2, billing.listForFirm(firm, PageRequest.of(0, 20)).getContent().size());
        denied(() -> billing.listForClient(firm, nkosi.getId(), PageRequest.of(0, 20)));
    }

    // ---- the client record ---------------------------------------------------------------------------------------

    @Test
    void aClientWallHidesTheClientRecordAndRefusesChangesToIt() {
        wallClient(dlamini);
        denied(() -> clientService.getClient(firm, dlamini.getId()));
        denied(() -> clientService.updateClient(firm, dlamini.getId(), new UpdateLpClientRequest("New name", null, null, null, null)));
        denied(() -> clientService.deactivateClient(firm, dlamini.getId()));
        assertTrue(saved.isEmpty());
        assertDoesNotThrow(() -> clientService.getClient(firm, nkosi.getId()));
        var list = clientService.listClients(firm, PageRequest.of(0, 20)).getContent();
        assertEquals(List.of(nkosi.getId()), list.stream().map(LpClientResponse::id).toList());
    }

    @Test
    void aMatterWallDoesNotHideTheClient() {
        wallMatter(d1);
        assertDoesNotThrow(() -> clientService.getClient(firm, dlamini.getId()));
        assertEquals(2, clientService.listClients(firm, PageRequest.of(0, 20)).getContent().size());
    }

    @Test
    void aPortalUserOrAnotherColleagueIsNotAffected() {
        wallClient(dlamini);
        signIn(colleague);
        assertDoesNotThrow(() -> clientService.getClient(firm, dlamini.getId()));
        TenantContext.clear();
        assertDoesNotThrow(() -> clientService.getClient(firm, dlamini.getId()));
    }

    // ---- the firm calendar and deadlines -------------------------------------------------------------------------

    @Test
    void theFirmCalendarLeavesOutKeyDatesOnWalledMattersAndClients() {
        keyDate(d1); keyDate(d2); keyDate(n1);
        LocalDate from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 12, 31);
        assertEquals(3, keyDateService.listForFirm(firm, from, to).size());
        wallMatter(d1);
        assertEquals(2, keyDateService.listForFirm(firm, from, to).size());
        wallClient(nkosi);
        var left = keyDateService.listForFirm(firm, from, to);
        assertEquals(1, left.size());
        assertEquals(d2.getId(), left.get(0).matterId());
    }

    @Test
    void deadlinesOnAWalledMatterAreRefused() {
        wallMatter(d1);
        denied(() -> deadlineService.compute(firm, d1.getId(), new ComputeDeadlineRequest(UUID.randomUUID(), LocalDate.of(2026, 11, 20), null, null), barred, "B"));
        denied(() -> deadlineService.listForMatter(firm, d1.getId()));
        assertDoesNotThrow(() -> deadlineService.listForMatter(firm, d2.getId()));
    }

    // ---- the guard's new door ------------------------------------------------------------------------------------

    @Test
    void aMatterlessRecordIsOnlyCoveredByAClientWall() {
        wallMatter(d1);
        assertDoesNotThrow(() -> guard.requireScopeAccess(firm, null, dlamini.getId()));
        wallClient(dlamini);
        denied(() -> guard.requireScopeAccess(firm, null, dlamini.getId()));
    }

    @Test
    void everyRefusalHereIsRecordedAgainstTheWall() {
        wallClient(dlamini);
        denied(() -> trust.recordReceipt(firm, dlamini.getId(), receiptOn(null), barred, "B"));
        denied(() -> billing.listForClient(firm, dlamini.getId(), PageRequest.of(0, 20)));
        assertEquals(2, denials.size());
        assertTrue(denials.stream().allMatch(d -> d.getUserId().equals(barred) && d.getClientId().equals(dlamini.getId())));
    }
}
