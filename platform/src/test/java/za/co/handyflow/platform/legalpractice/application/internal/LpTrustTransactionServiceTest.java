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
 * The trust ledger's money rules, run against in-memory stand-ins for the repositories (no database, no Spring).
 * The migration's own behaviour (append-only trigger, backfill, unique key) was run against PostgreSQL separately.
 */
class LpTrustTransactionServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());

    private final Map<UUID, LpClient> clients = new HashMap<>();
    private final Map<UUID, LpInvoice> invoices = new HashMap<>();
    private final Map<UUID, LpMatter> matters = new HashMap<>();
    private final List<LpTrustTransaction> ledger = new ArrayList<>();
    private final List<String> posted = new ArrayList<>();
    private final LpAccountingPoster poster = new LpAccountingPoster(null) {
        @Override public void postInvoiceRevenue(TenantId tenantId, String invoiceNumber, BigDecimal amount) { posted.add(invoiceNumber + ":" + amount); }
    };

    private LpTrustTransactionService trust;
    private LpMatterService matterService;
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

    private static LpTrustTransactionService.TrustApproval approval() {
        return new LpTrustTransactionService.TrustApproval(UUID.randomUUID(), UUID.randomUUID(), "Partner");
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        clients.clear(); invoices.clear(); matters.clear(); ledger.clear();
        var clientRepo = fake(LpClientRepository.class, Map.of(
                "findActiveById", a -> ownedBy(clients, a[0], a[1], LpClient::getTenantId),
                "save", a -> { LpClient c = (LpClient) a[0]; clients.put(c.getId(), c); return c; }));
        var invoiceRepo = fake(LpInvoiceRepository.class, Map.of(
                "findActiveById", a -> ownedBy(invoices, a[0], a[1], LpInvoice::getTenantId),
                "save", a -> { LpInvoice i = (LpInvoice) a[0]; invoices.put(i.getId(), i); return i; }));
        var matterRepo = fake(LpMatterRepository.class, Map.of(
                "findActiveById", a -> ownedBy(matters, a[0], a[1], LpMatter::getTenantId),
                "save", a -> { LpMatter m = (LpMatter) a[0]; matters.put(m.getId(), m); return m; }));
        var trustRepo = fake(LpTrustTransactionRepository.class, Map.of(
                "save", a -> { ledger.add((LpTrustTransaction) a[0]); return a[0]; },
                "findByIdempotencyKey", a -> ledger.stream().filter(t -> t.getTenantId().equals(a[0]) && a[1].equals(t.getIdempotencyKey())).findFirst(),
                "netHeldForMatterRaw", a -> ledger.stream().filter(t -> t.getTenantId().getValue().equals(a[0]) && a[1].equals(t.getMatterId()))
                        .map(t -> t.increasesTrustBalance() ? t.getAmount() : t.getAmount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        trust = new LpTrustTransactionService(trustRepo, clientRepo, invoiceRepo, matterRepo, poster, LpWallGuard.NONE, LpKycGate.NONE);
        matterService = new LpMatterService(matterRepo, trustRepo, null, LpWallGuard.NONE, LpKycGate.NONE);
        client = LpClient.create(firm, "Dlamini Trading", null, null, "COMPANY", null, null);
        clients.put(client.getId(), client);
    }

    private LpMatter matterFor(LpClient c, TenantId t) {
        LpMatter m = LpMatter.create(t, c.getId(), UUID.randomUUID(), "M-1", "LITIGATION", "Dispute", null, "HOURLY", null, LocalDate.now(), null);
        matters.put(m.getId(), m);
        return m;
    }

    private LpInvoice sentInvoice(String subtotal) {
        LpInvoice i = LpInvoice.create(firm, client.getId(), null, "INV-1", null, LocalDate.now(), null, bd(subtotal), null, bd("0"));
        i.markSent();
        invoices.put(i.getId(), i);
        return i;
    }

    private LpTrustTransactionResponse receipt(String amount, UUID matterId, String key) {
        return trust.recordReceipt(firm, client.getId(), new RecordTrustReceiptRequest(matterId, bd(amount), null, null, null, key), UUID.randomUUID(), "Clerk");
    }

    private LpTrustTransactionResponse pay(String amount, String key) {
        return trust.payDisbursement(firm, client.getId(), new PayDisbursementFromTrustRequest(null, bd(amount), null, "Sheriff", null, null, key), UUID.randomUUID(), "Admin", approval());
    }

    @Test
    void aPaymentWithoutAnApprovedRequestIsRefused() {
        receipt("100", null, null);
        var req = new PayDisbursementFromTrustRequest(null, bd("10"), null, "Sheriff", null, null, null);
        assertThrows(IllegalStateException.class, () -> trust.payDisbursement(firm, client.getId(), req, UUID.randomUUID(), "Admin", null));
        assertThrows(IllegalStateException.class, () -> trust.payDisbursement(firm, client.getId(), req, UUID.randomUUID(), "Admin",
                new LpTrustTransactionService.TrustApproval(null, UUID.randomUUID(), "x")));
        assertEquals(1, ledger.size());
        assertEquals(bd("100"), client.getTrustBalance());
    }

    @Test
    void anApprovedPaymentRecordsWhoApprovedItAndFromWhichRequest() {
        receipt("100", null, null);
        var a = approval();
        var r = trust.payDisbursement(firm, client.getId(), new PayDisbursementFromTrustRequest(null, bd("10"), null, "Sheriff", null, null, null),
                UUID.randomUUID(), "Clerk", a);
        assertEquals("Partner", r.approvedByName());
        assertEquals(a.requestId(), ledger.get(1).getRequestId());
        assertEquals(a.approvedBy(), ledger.get(1).getApprovedBy());
        assertEquals("Clerk", ledger.get(1).getCapturedByName());
    }

    @Test
    void everyRowRecordsTheBalanceItLeftBehind() {
        assertEquals(bd("1000.00"), receipt("1000.00", null, null).clientTrustBalanceAfter());
        assertEquals(bd("749.50"), pay("250.50", null).clientTrustBalanceAfter());
        assertEquals(bd("749.50"), client.getTrustBalance());
        assertEquals(2, ledger.size());
        assertEquals(bd("749.50"), ledger.get(1).getBalanceAfter());
    }

    @Test
    void listedRowsCarryTheirBalanceAfterNotNull() {
        receipt("100", null, null);
        // the mapping used by the list endpoint is the same one used after posting
        assertEquals(bd("100"), ledger.get(0).getBalanceAfter());
    }

    @Test
    void aWithdrawalBeyondTheBalanceIsRefusedAndLeavesNothingBehind() {
        receipt("100", null, null);
        assertThrows(IllegalStateException.class, () -> pay("100.01", null));
        assertEquals(1, ledger.size());
        assertEquals(bd("100"), client.getTrustBalance());
    }

    @Test
    void aTransferCannotExceedWhatTheInvoiceStillOwes() {
        receipt("5000", null, null);
        LpInvoice inv = sentInvoice("1000.00");
        var tooMuch = new TransferToBusinessRequest(inv.getId(), null, bd("1000.01"), null, null, null, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> trust.transferToBusiness(firm, client.getId(), tooMuch, UUID.randomUUID(), "Admin", approval()));
        assertTrue(e.getMessage().contains("still owes"));
        assertEquals(1, ledger.size(), "no transfer row");
        assertEquals(bd("5000"), client.getTrustBalance());
        assertEquals(0, inv.getAmountPaid().signum(), "the invoice was not touched");
        assertTrue(posted.isEmpty(), "nothing posted to the ledger");
    }

    @Test
    void aTransferOfExactlyWhatIsOwedSettlesTheInvoice() {
        receipt("5000", null, null);
        LpInvoice inv = sentInvoice("1000.00");
        var r = trust.transferToBusiness(firm, client.getId(), new TransferToBusinessRequest(inv.getId(), null, bd("1000.00"), null, null, null, null), UUID.randomUUID(), "Admin", approval());
        assertEquals(bd("4000.00"), r.clientTrustBalanceAfter());
        assertEquals("PAID", inv.getStatus());
        assertEquals(1, posted.size());
    }

    @Test
    void aMatterMustBeThisClientsOwnAndThisFirms() {
        LpClient other = LpClient.create(firm, "Someone Else", null, null, "COMPANY", null, null);
        clients.put(other.getId(), other);
        LpMatter othersMatter = matterFor(other, firm);
        assertThrows(IllegalArgumentException.class, () -> receipt("10", othersMatter.getId(), null));

        LpMatter foreign = matterFor(client, otherFirm);   // another firm's matter, even though the client id matches
        assertThrows(ResourceNotFoundException.class, () -> receipt("10", foreign.getId(), null));
        assertThrows(ResourceNotFoundException.class, () -> receipt("10", UUID.randomUUID(), null));
        assertTrue(ledger.isEmpty());

        LpMatter mine = matterFor(client, firm);
        receipt("10", mine.getId(), null);
        assertEquals(1, ledger.size());
    }

    @Test
    void amountsAreWholeCents() {
        assertThrows(IllegalArgumentException.class, () -> receipt("10.005", null, null));
        assertEquals(0, ledger.size());
        receipt("10.50", null, null);
        receipt("10.500", null, null);   // trailing zeros are not extra precision
        assertEquals(2, ledger.size());
    }

    @Test
    void theSameKeyPostsOnce() {
        var first = receipt("100", null, "attempt-1");
        var again = receipt("100", null, "attempt-1");
        assertEquals(first.id(), again.id());
        assertEquals(1, ledger.size());
        assertEquals(bd("100"), client.getTrustBalance());
    }

    @Test
    void aKeyReusedForSomethingElseIsRefused() {
        receipt("100", null, "k");
        assertThrows(IllegalStateException.class, () -> receipt("200", null, "k"));
        assertThrows(IllegalStateException.class, () -> pay("50", "k"));
        assertEquals(1, ledger.size());
        assertEquals(bd("100"), client.getTrustBalance());
    }

    @Test
    void aBlankKeyMeansNoKey() {
        receipt("5", null, "  ");
        receipt("5", null, "");
        assertEquals(2, ledger.size());
        assertNull(ledger.get(0).getIdempotencyKey());
    }

    @Test
    void aMatterCannotCloseWhileItHoldsClientMoney() {
        LpMatter m = matterFor(client, firm);
        receipt("300", m.getId(), null);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> matterService.close(firm, m.getId(), new CloseMatterRequest(null)));
        assertTrue(e.getMessage().contains("held in trust"));
        assertEquals("OPEN", m.getStatus());

        trust.refund(firm, client.getId(), new RefundTrustRequest(m.getId(), bd("300"), null, "Dlamini Trading", null, null, null), UUID.randomUUID(), "Admin", approval());
        matterService.close(firm, m.getId(), new CloseMatterRequest(null));
        assertEquals("CLOSED", m.getStatus());
    }

    @Test
    void aSealedRowCannotBeSealedAgainAndNeverRecordsANegativeBalance() {
        receipt("10", null, null);
        LpTrustTransaction t = ledger.get(0);
        assertThrows(IllegalStateException.class, () -> t.seal(bd("1"), null));
        LpTrustTransaction fresh = LpTrustTransaction.create(firm, client.getId(), null, "RECEIPT", bd("1"), null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> fresh.seal(bd("-0.01"), null));
    }
}
