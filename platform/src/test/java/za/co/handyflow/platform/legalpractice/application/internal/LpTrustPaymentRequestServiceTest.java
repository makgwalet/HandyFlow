package za.co.handyflow.platform.legalpractice.application.internal;

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
 * Maker-checker for payments out of trust, run against in-memory stand-ins for the repositories (no database, no Spring).
 * The migration's own constraints and triggers were run against PostgreSQL separately.
 */
class LpTrustPaymentRequestServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();

    private final Map<UUID, LpClient> clients = new HashMap<>();
    private final Map<UUID, LpInvoice> invoices = new HashMap<>();
    private final Map<UUID, LpMatter> matters = new HashMap<>();
    private final Map<UUID, LpTrustPaymentRequest> requests = new HashMap<>();
    private final List<LpTrustTransaction> ledger = new ArrayList<>();
    private final List<LpTrustSettingChange> settingRows = new ArrayList<>();
    private final List<String> posted = new ArrayList<>();
    private final LpAccountingPoster poster = new LpAccountingPoster(null) {
        @Override public void postInvoiceRevenue(TenantId tenantId, String invoiceNumber, BigDecimal amount) { posted.add(invoiceNumber + ":" + amount); }
    };

    private LpTrustPaymentRequestService service;
    private LpTrustSettingsService settings;
    private LpClient client;

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

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        clients.clear(); invoices.clear(); matters.clear(); requests.clear(); ledger.clear(); settingRows.clear(); posted.clear();
        var clientRepo = fake(LpClientRepository.class, Map.of(
                "findActiveById", a -> ownedBy(clients, a[0], a[1], LpClient::getTenantId),
                "save", a -> { LpClient c = (LpClient) a[0]; clients.put(c.getId(), c); return c; }));
        var invoiceRepo = fake(LpInvoiceRepository.class, Map.of(
                "findActiveById", a -> ownedBy(invoices, a[0], a[1], LpInvoice::getTenantId),
                "save", a -> { LpInvoice i = (LpInvoice) a[0]; invoices.put(i.getId(), i); return i; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> ownedBy(matters, a[0], a[1], LpMatter::getTenantId)));
        var trustRepo = fake(LpTrustTransactionRepository.class, Map.of(
                "save", a -> { ledger.add((LpTrustTransaction) a[0]); return a[0]; },
                "findByIdempotencyKey", a -> ledger.stream().filter(t -> t.getTenantId().equals(a[0]) && a[1].equals(t.getIdempotencyKey())).findFirst()));
        var requestRepo = fake(LpTrustPaymentRequestRepository.class, Map.of(
                "findActiveById", a -> ownedBy(requests, a[0], a[1], LpTrustPaymentRequest::getTenantId),
                "findByIdempotencyKey", a -> requests.values().stream().filter(r -> r.getTenantId().equals(a[0]) && a[1].equals(r.getIdempotencyKey())).findFirst(),
                "save", a -> { LpTrustPaymentRequest r = (LpTrustPaymentRequest) a[0]; requests.put(r.getId(), r); return r; }));
        var settingRepo = fake(LpTrustSettingChangeRepository.class, Map.of(
                "save", a -> { settingRows.add((LpTrustSettingChange) a[0]); return a[0]; },
                "latest", a -> { List<LpTrustSettingChange> mine = new ArrayList<>(); for (var c : settingRows) if (c.getTenantId().equals(a[0])) mine.add(0, c); return mine; }));
        var trust = new LpTrustTransactionService(trustRepo, clientRepo, invoiceRepo, matterRepo, poster, LpWallGuard.NONE, LpKycGate.NONE);
        settings = new LpTrustSettingsService(settingRepo);
        service = new LpTrustPaymentRequestService(requestRepo, clientRepo, trust, settings, LpWallGuard.NONE);
        client = LpClient.create(firm, "Dlamini Trading", null, null, "COMPANY", null, null);
        client.increaseTrustBalance(bd("1000.00"));
        clients.put(client.getId(), client);
    }

    private CreateTrustPaymentRequest disbursement(String amount) {
        return new CreateTrustPaymentRequest("DISBURSEMENT_PAYMENT", null, null, bd(amount), null, "Sheriff of the Court", "REF-1", null, null);
    }

    private LpInvoice sentInvoice(String subtotal) {
        LpInvoice i = LpInvoice.create(firm, client.getId(), null, "INV-1", null, LocalDate.now(), null, bd(subtotal), null, bd("0"));
        i.markSent();
        invoices.put(i.getId(), i);
        return i;
    }

    private LpTrustPaymentRequestResponse ask(String amount) {
        return service.create(firm, client.getId(), disbursement(amount), maker, "Clerk");
    }

    private CreateTrustPaymentRequest keyed(String amount, String key) {
        return new CreateTrustPaymentRequest("DISBURSEMENT_PAYMENT", null, null, bd(amount), null, "Sheriff of the Court", null, null, key);
    }

    @Test
    void theSameAttemptSentTwiceQueuesOneRequest() {
        var a = service.create(firm, client.getId(), keyed("100", "k1"), maker, "Clerk");
        var b = service.create(firm, client.getId(), keyed("100", "k1"), maker, "Clerk");
        assertEquals(a.id(), b.id());
        assertEquals(1, requests.size());
    }

    @Test
    void aKeyReusedForADifferentRequestOrByAnotherPersonIsRefused() {
        service.create(firm, client.getId(), keyed("100", "k1"), maker, "Clerk");
        assertThrows(IllegalStateException.class, () -> service.create(firm, client.getId(), keyed("200", "k1"), maker, "Clerk"));
        assertThrows(IllegalStateException.class, () -> service.create(firm, client.getId(), keyed("100", "k1"), checker, "Partner"));
        assertEquals(1, requests.size());
    }

    @Test
    void aBlankKeyMeansNoKeyAndTwoRequestsAreQueued() {
        service.create(firm, client.getId(), keyed("100", " "), maker, "Clerk");
        service.create(firm, client.getId(), keyed("100", null), maker, "Clerk");
        assertEquals(2, requests.size());
    }

    @Test
    void aRequestCarriesTheClientsName() {
        assertEquals("Dlamini Trading", ask("10").clientName());
    }

    @Test
    void requestingMovesNoMoney() {
        var r = ask("250.00");
        assertEquals("PENDING", r.status());
        assertEquals(bd("1000.00"), client.getTrustBalance());
        assertTrue(ledger.isEmpty());
    }

    @Test
    void aDifferentPersonApprovesAndTheMoneyMovesOnce() {
        var r = ask("250.00");
        var done = service.approve(firm, r.id(), checker, "Partner", null);
        assertEquals("APPROVED", done.status());
        assertFalse(done.selfApproved());
        assertEquals(bd("750.00"), client.getTrustBalance());
        assertEquals(1, ledger.size());
        LpTrustTransaction tx = ledger.get(0);
        assertEquals(done.resultTransactionId(), tx.getId());
        assertEquals(r.id(), tx.getRequestId());
        assertEquals(checker, tx.getApprovedBy());
        assertEquals(maker, tx.getCapturedBy(), "the ledger row is captured by the requester");
        assertEquals("payment-request:" + r.id(), tx.getIdempotencyKey());
    }

    @Test
    void theRequesterCannotApproveTheirOwnPaymentByDefault() {
        var r = ask("250.00");
        assertThrows(IllegalStateException.class, () -> service.approve(firm, r.id(), maker, "Clerk", "I am the only one here"));
        assertEquals("PENDING", requests.get(r.id()).getStatus());
        assertTrue(ledger.isEmpty());
        assertEquals(bd("1000.00"), client.getTrustBalance());
    }

    @Test
    void whenTheFirmAllowsItASelfApprovalNeedsAWrittenReasonAndIsFlagged() {
        settings.update(firm, true, "Sole practitioner with no second signatory", UUID.randomUUID(), "Owner");
        var r = ask("100.00");
        assertThrows(IllegalArgumentException.class, () -> service.approve(firm, r.id(), maker, "Clerk", null));
        assertThrows(IllegalArgumentException.class, () -> service.approve(firm, r.id(), maker, "Clerk", "short"));
        assertTrue(ledger.isEmpty());
        var done = service.approve(firm, r.id(), maker, "Clerk", "Sole practitioner, paying the sheriff's fee");
        assertTrue(done.selfApproved());
        assertEquals("Sole practitioner, paying the sheriff's fee", done.decisionReason());
        assertEquals(bd("900.00"), client.getTrustBalance());
    }

    @Test
    void aSecondPersonNeverNeedsTheExceptionOrAReason() {
        settings.update(firm, true, "Sole practitioner with no second signatory", UUID.randomUUID(), "Owner");
        var r = ask("100.00");
        var done = service.approve(firm, r.id(), checker, "Partner", "ignored");
        assertFalse(done.selfApproved());
        assertNull(done.decisionReason());
    }

    @Test
    void tighteningTheSettingAgainRestoresTheStrictRule() {
        settings.update(firm, true, "Sole practitioner with no second signatory", UUID.randomUUID(), "Owner");
        settings.update(firm, false, "A second attorney has joined the firm", UUID.randomUUID(), "Owner");
        assertFalse(settings.selfApprovalAllowed(firm));
        var r = ask("100.00");
        assertThrows(IllegalStateException.class, () -> service.approve(firm, r.id(), maker, "Clerk", "Long enough reason here"));
        assertEquals(2, settings.get(firm).history().size());
        assertEquals("A second attorney has joined the firm", settings.get(firm).history().get(0).reason());
    }

    @Test
    void theSettingIsStrictByDefaultAndPerFirm() {
        assertFalse(settings.selfApprovalAllowed(firm));
        settings.update(firm, true, "Sole practitioner with no second signatory", UUID.randomUUID(), "Owner");
        assertTrue(settings.selfApprovalAllowed(firm));
        assertFalse(settings.selfApprovalAllowed(otherFirm));
    }

    @Test
    void changingTheSettingNeedsAReasonAndAnActualChange() {
        assertThrows(IllegalArgumentException.class, () -> settings.update(firm, true, "short", UUID.randomUUID(), "Owner"));
        assertThrows(IllegalStateException.class, () -> settings.update(firm, false, "Already strict, nothing to do", UUID.randomUUID(), "Owner"));
        assertTrue(settingRows.isEmpty());
    }

    @Test
    void ifPostingFailsAtApprovalNothingChangesAndTheRequestStaysPending() {
        var r = ask("900.00");
        client.decreaseTrustBalance(bd("500.00")); // someone else paid in the meantime
        assertThrows(IllegalStateException.class, () -> service.approve(firm, r.id(), checker, "Partner", null));
        assertEquals("PENDING", requests.get(r.id()).getStatus());
        assertNull(requests.get(r.id()).getResultTransactionId());
        assertTrue(ledger.isEmpty());
        assertEquals(bd("500.00"), client.getTrustBalance());
    }

    @Test
    void aRequestThatCouldNeverBePaidIsRefusedWhenMade() {
        assertThrows(IllegalStateException.class, () -> ask("1000.01"));
        assertThrows(IllegalArgumentException.class, () -> ask("10.005"));
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, client.getId(),
                new CreateTrustPaymentRequest("DISBURSEMENT_PAYMENT", null, null, bd("10"), null, " ", null, null, null), maker, "Clerk"));
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, client.getId(),
                new CreateTrustPaymentRequest("RECEIPT", null, null, bd("10"), null, "x", null, null, null), maker, "Clerk"));
        assertThrows(ResourceNotFoundException.class, () -> service.create(otherFirm, client.getId(), disbursement("10"), maker, "Clerk"));
        assertTrue(requests.isEmpty());
    }

    @Test
    void aTransferToTheBusinessIsCappedByTheInvoiceAtRequestAndAtApproval() {
        LpInvoice inv = sentInvoice("400.00");
        assertThrows(IllegalArgumentException.class, () -> service.create(firm, client.getId(),
                new CreateTrustPaymentRequest("TRANSFER_TO_BUSINESS", null, inv.getId(), bd("400.01"), null, null, null, null, null), maker, "Clerk"));
        var r = service.create(firm, client.getId(),
                new CreateTrustPaymentRequest("TRANSFER_TO_BUSINESS", null, inv.getId(), bd("400.00"), null, null, null, null, null), maker, "Clerk");
        inv.applyPayment(bd("100.00")); // the client paid part of it directly in the meantime
        assertThrows(IllegalArgumentException.class, () -> service.approve(firm, r.id(), checker, "Partner", null));
        assertEquals("PENDING", requests.get(r.id()).getStatus());
        assertEquals(bd("1000.00"), client.getTrustBalance());
        assertTrue(posted.isEmpty());
    }

    @Test
    void anApprovedTransferSettlesTheInvoiceAndPostsToTheGeneralLedger() {
        LpInvoice inv = sentInvoice("400.00");
        var r = service.create(firm, client.getId(),
                new CreateTrustPaymentRequest("TRANSFER_TO_BUSINESS", null, inv.getId(), bd("400.00"), null, null, null, null, null), maker, "Clerk");
        service.approve(firm, r.id(), checker, "Partner", null);
        assertEquals(bd("600.00"), client.getTrustBalance());
        assertEquals(bd("400.00"), inv.getAmountPaid());
        assertEquals(List.of("INV-1:400.00"), posted);
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgainSoNothingIsPaidTwice() {
        var r = ask("100.00");
        service.approve(firm, r.id(), checker, "Partner", null);
        assertThrows(IllegalStateException.class, () -> service.approve(firm, r.id(), checker, "Partner", null));
        assertThrows(IllegalStateException.class, () -> service.reject(firm, r.id(), checker, "Partner", "Changed my mind, sorry"));
        assertThrows(IllegalStateException.class, () -> service.cancel(firm, r.id(), maker, "Clerk"));
        assertEquals(1, ledger.size());
        assertEquals(bd("900.00"), client.getTrustBalance());
    }

    @Test
    void rejectingNeedsAReasonAndMovesNoMoney() {
        var r = ask("100.00");
        assertThrows(IllegalArgumentException.class, () -> service.reject(firm, r.id(), checker, "Partner", "no"));
        var done = service.reject(firm, r.id(), checker, "Partner", "Payee details do not match the invoice");
        assertEquals("REJECTED", done.status());
        assertEquals("Payee details do not match the invoice", done.decisionReason());
        assertTrue(ledger.isEmpty());
        assertEquals(bd("1000.00"), client.getTrustBalance());
    }

    @Test
    void onlyTheRequesterCanWithdrawARequest() {
        var r = ask("100.00");
        assertThrows(IllegalStateException.class, () -> service.cancel(firm, r.id(), checker, "Partner"));
        assertEquals("CANCELLED", service.cancel(firm, r.id(), maker, "Clerk").status());
        assertThrows(IllegalStateException.class, () -> service.approve(firm, r.id(), checker, "Partner", null));
        assertTrue(ledger.isEmpty());
    }

    @Test
    void anotherFirmCannotSeeOrDecideARequest() {
        var r = ask("100.00");
        assertThrows(ResourceNotFoundException.class, () -> service.approve(otherFirm, r.id(), checker, "Partner", null));
        assertThrows(ResourceNotFoundException.class, () -> service.reject(otherFirm, r.id(), checker, "Partner", "Not our request at all"));
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(otherFirm, r.id(), maker, "Clerk"));
        assertEquals("PENDING", requests.get(r.id()).getStatus());
    }

    @Test
    void anUnknownStatusFilterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.list(firm, "DONE", null));
    }
}
