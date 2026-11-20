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
 * Trust reconciliation against in-memory stand-ins for the repositories (no database, no Spring). The migration's constraints,
 * triggers and the period lock were run against PostgreSQL separately.
 */
class LpTrustReconciliationServiceTest {

    private final TenantId firm = TenantId.of(UUID.randomUUID());
    private final TenantId otherFirm = TenantId.of(UUID.randomUUID());
    private final UUID preparer = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();
    private final UUID clientA = UUID.randomUUID();
    private final UUID clientB = UUID.randomUUID();

    private final Map<UUID, LpTrustReconciliation> recons = new LinkedHashMap<>();
    private final Map<UUID, LpTrustReconItem> items = new LinkedHashMap<>();
    private final List<LpTrustTransaction> ledger = new ArrayList<>();
    private final Map<UUID, Object[]> stored = new LinkedHashMap<>(); // clientId -> [id, name, trustBalance]
    private final List<LpTrustSettingChange> settingRows = new ArrayList<>();
    private LpTrustReconciliationService service;
    private LpTrustSettingsService settings;

    private final LocalDate oct5 = LocalDate.of(2026, 10, 5), oct20 = LocalDate.of(2026, 10, 20), oct29 = LocalDate.of(2026, 10, 29),
            oct30 = LocalDate.of(2026, 10, 30), oct31 = LocalDate.of(2026, 10, 31);

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

    private static BigDecimal signed(LpTrustTransaction t) { return t.increasesTrustBalance() ? t.getAmount() : t.getAmount().negate(); }

    @BeforeEach
    void setUp() {
        recons.clear(); items.clear(); ledger.clear(); stored.clear(); settingRows.clear();
        var reconRepo = fake(LpTrustReconciliationRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(recons.get((UUID) a[1])).filter(r -> r.getTenantId().equals(a[0])),
                "findOpen", a -> recons.values().stream().filter(r -> r.getTenantId().equals(a[0]) && !r.getStatus().equals("APPROVED")).toList(),
                "findApproved", a -> recons.values().stream().filter(r -> r.getTenantId().equals(a[0]) && r.getStatus().equals("APPROVED"))
                        .sorted(Comparator.comparing(LpTrustReconciliation::getPeriodEnd).reversed()).toList(),
                "save", a -> { LpTrustReconciliation r = (LpTrustReconciliation) a[0]; recons.put(r.getId(), r); return r; }));
        var itemRepo = fake(LpTrustReconItemRepository.class, Map.of(
                "findActiveById", a -> Optional.ofNullable(items.get((UUID) a[1])).filter(i -> i.getTenantId().equals(a[0])),
                "findForReconciliation", a -> items.values().stream().filter(i -> i.getTenantId().equals(a[0]) && i.getReconciliationId().equals(a[1]))
                        .sorted(Comparator.comparing(LpTrustReconItem::getItemDate)).toList(),
                "save", a -> { LpTrustReconItem i = (LpTrustReconItem) a[0]; items.put(i.getId(), i); return i; },
                "delete", a -> { items.remove(((LpTrustReconItem) a[0]).getId()); return null; }));
        var ledgerRepo = fake(LpTrustLedgerReadRepository.class, Map.of(
                "cashBookAsAt", a -> ledger.stream().filter(t -> t.getTenantId().equals(a[0]) && !t.getTransactionDate().isAfter((LocalDate) a[1])).map(LpTrustReconciliationServiceTest::signed).reduce(BigDecimal.ZERO, BigDecimal::add),
                "clientBalancesAsAt", a -> {
                    Map<UUID, BigDecimal> m = new LinkedHashMap<>();
                    for (LpTrustTransaction t : ledger) if (t.getTenantId().equals(a[0]) && !t.getTransactionDate().isAfter((LocalDate) a[1])) m.merge(t.getClientId(), signed(t), BigDecimal::add);
                    List<Object[]> rows = new ArrayList<>(); m.forEach((k, v) -> rows.add(new Object[]{k, v})); return rows;
                },
                "storedClientBalances", a -> new ArrayList<>(stored.values())));
        var trustRepo = fake(LpTrustTransactionRepository.class, Map.of(
                "findActiveById", a -> ledger.stream().filter(t -> t.getTenantId().equals(a[0]) && t.getId().equals(a[1])).findFirst()));
        var settingRepo = fake(LpTrustSettingChangeRepository.class, Map.of(
                "save", a -> { settingRows.add((LpTrustSettingChange) a[0]); return a[0]; },
                "latest", a -> { List<LpTrustSettingChange> mine = new ArrayList<>(); for (var c : settingRows) if (c.getTenantId().equals(a[0])) mine.add(0, c); return mine; }));
        settings = new LpTrustSettingsService(settingRepo);
        service = new LpTrustReconciliationService(reconRepo, itemRepo, ledgerRepo, trustRepo, settings);
        stored.put(clientA, new Object[]{clientA, "Dlamini", new BigDecimal("9000.00")});
    }

    private LpTrustTransaction receipt(TenantId t, UUID client, String amount, LocalDate d) {
        var tx = LpTrustTransaction.create(t, client, null, "RECEIPT", new BigDecimal(amount), d, null, null, "ref", preparer, "Clerk", null);
        ledger.add(tx); return tx;
    }
    private LpTrustTransaction payment(TenantId t, UUID client, String amount, LocalDate d) {
        var tx = LpTrustTransaction.create(t, client, null, "DISBURSEMENT_PAYMENT", new BigDecimal(amount), d, null, "Sheriff", "ref", preparer, "Clerk", null);
        ledger.add(tx); return tx;
    }

    /** The ledger: +10,000 (5 Oct), -2,000 (20 Oct), -500 (29 Oct), +1,500 (30 Oct) = 9,000. The bank has not yet seen the last two. */
    private LpTrustTransaction[] october() {
        receipt(firm, clientA, "10000", oct5);
        payment(firm, clientA, "2000", oct20);
        return new LpTrustTransaction[]{payment(firm, clientA, "500", oct29), receipt(firm, clientA, "1500", oct30)};
    }

    private TrustReconciliationResponse start(String statement) { return service.start(firm, new StartReconciliationRequest(oct31, new BigDecimal(statement), null), preparer, "Clerk"); }
    private void item(UUID id, String kind, String amount, LocalDate d, UUID tx) { service.addItem(firm, id, new AddReconItemRequest(kind, new BigDecimal(amount), d, "In transit", null, tx), preparer); }

    private UUID balancedAndSubmitted() {
        var tx = october();
        var r = start("8000.00");
        item(r.id(), "OUTSTANDING_DEPOSIT", "1500", oct30, tx[1].getId());
        item(r.id(), "OUTSTANDING_PAYMENT", "500", oct29, tx[0].getId());
        service.submit(firm, r.id(), preparer);
        return r.id();
    }

    // ── figures ──────────────────────────────────────────────────────────────

    @Test
    void theArithmetic() {
        var f = ReconciliationFigures.of(new BigDecimal("8000"), new BigDecimal("1500"), new BigDecimal("500"), new BigDecimal("9000"), new BigDecimal("9000"));
        assertEquals(0, new BigDecimal("9000").compareTo(f.adjustedBankBalance()));
        assertEquals(0, f.difference().signum());
        assertTrue(f.balanced());
        var off = ReconciliationFigures.of(new BigDecimal("8000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("9000"), new BigDecimal("9000"));
        assertEquals(0, new BigDecimal("1000").compareTo(off.difference()));
        assertFalse(off.balanced());
        assertFalse(ReconciliationFigures.of(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("11")).balanced(), "client ledgers must add up to the cash book too");
    }

    @Test
    void aDraftShowsTheFiguresLiveAndWhatIsLeftUnexplained() {
        october();
        var r = start("8000.00");
        assertEquals("DRAFT", r.status());
        assertTrue(r.live());
        assertEquals(0, new BigDecimal("9000").compareTo(r.cashBookBalance()));
        assertEquals(0, new BigDecimal("1000").compareTo(r.difference()));
        assertFalse(r.balanced());
        item(r.id(), "OUTSTANDING_DEPOSIT", "1500", oct30, null);
        var after = service.get(firm, r.id());
        assertEquals(0, new BigDecimal("-500").compareTo(after.difference()), "1,500 too much explained");
        item(r.id(), "OUTSTANDING_PAYMENT", "500", oct29, null);
        assertTrue(service.get(firm, r.id()).balanced());
    }

    @Test
    void theLedgerIsReadAsAtThePeriodEndNotAsOfToday() {
        october();
        receipt(firm, clientA, "700", LocalDate.of(2026, 11, 2));
        var r = start("8000.00");
        assertEquals(0, new BigDecimal("9000").compareTo(r.cashBookBalance()), "a November receipt is not in the October ledger");
    }

    @Test
    void anotherFirmsLedgerIsNotCounted() {
        october();
        receipt(otherFirm, UUID.randomUUID(), "99999", oct5);
        assertEquals(0, new BigDecimal("9000").compareTo(start("8000").cashBookBalance()));
    }

    // ── starting ─────────────────────────────────────────────────────────────

    @Test
    void aReconciliationCannotBeDatedInTheFutureOrStartedWhileAnotherIsOpen() {
        assertThrows(IllegalArgumentException.class, () -> service.start(firm, new StartReconciliationRequest(LocalDate.now().plusDays(1), BigDecimal.ZERO, null), preparer, "Clerk"));
        start("0");
        assertThrows(IllegalStateException.class, () -> start("0"));
        assertNotNull(service.start(otherFirm, new StartReconciliationRequest(oct31, BigDecimal.ZERO, null), preparer, "Clerk"), "another firm has its own");
    }

    // ── items ────────────────────────────────────────────────────────────────

    @Test
    void anItemMustBeDatedWithinThePeriodAndHaveRealValues() {
        var r = start("8000");
        assertThrows(IllegalArgumentException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "100", LocalDate.of(2026, 11, 1), null));
        assertThrows(IllegalArgumentException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "0", oct30, null));
        assertThrows(IllegalArgumentException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "-5", oct30, null));
        assertThrows(IllegalArgumentException.class, () -> item(r.id(), "BANK_CHARGE", "5", oct30, null));
        assertThrows(IllegalArgumentException.class, () -> service.addItem(firm, r.id(), new AddReconItemRequest("OUTSTANDING_DEPOSIT", BigDecimal.TEN, oct30, "ab", null, null), preparer));
        assertTrue(items.isEmpty());
    }

    @Test
    void aLinkedItemMustMatchItsLedgerEntryAndIsListedOnce() {
        var tx = october();
        var r = start("8000");
        assertThrows(IllegalArgumentException.class, () -> item(r.id(), "OUTSTANDING_PAYMENT", "1500", oct30, tx[1].getId()), "a receipt is not an outstanding payment");
        assertThrows(IllegalArgumentException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "1400", oct30, tx[1].getId()), "amount differs from the ledger entry");
        assertThrows(ResourceNotFoundException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "1500", oct30, UUID.randomUUID()));
        item(r.id(), "OUTSTANDING_DEPOSIT", "1500", oct30, tx[1].getId());
        assertThrows(IllegalStateException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "1500", oct30, tx[1].getId()));
        var late = receipt(firm, clientA, "50", LocalDate.of(2026, 11, 3));
        assertThrows(IllegalArgumentException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "50", oct30, late.getId()), "dated after the reconciliation date");
        var theirs = receipt(otherFirm, UUID.randomUUID(), "10", oct5);
        assertThrows(ResourceNotFoundException.class, () -> item(r.id(), "OUTSTANDING_DEPOSIT", "10", oct5, theirs.getId()));
    }

    @Test
    void anItemCanBeRemovedOnlyFromItsOwnDraft() {
        var r = start("8000");
        item(r.id(), "OUTSTANDING_DEPOSIT", "1500", oct30, null);
        UUID itemId = items.keySet().iterator().next();
        assertThrows(ResourceNotFoundException.class, () -> service.removeItem(firm, r.id(), UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.removeItem(otherFirm, r.id(), itemId));
        assertTrue(service.removeItem(firm, r.id(), itemId).items().isEmpty());
    }

    // ── submitting ───────────────────────────────────────────────────────────

    @Test
    void anUnbalancedReconciliationCannotBeSubmitted() {
        october();
        var r = start("8000");
        var ex = assertThrows(IllegalStateException.class, () -> service.submit(firm, r.id(), preparer));
        assertTrue(ex.getMessage().contains("1000"), ex.getMessage());
        assertEquals("DRAFT", recons.get(r.id()).getStatus());
    }

    @Test
    void submittingFreezesTheFiguresAndLocksTheDraftEditing() {
        UUID id = balancedAndSubmitted();
        var s = recons.get(id);
        assertEquals("SUBMITTED", s.getStatus());
        assertEquals(0, new BigDecimal("9000").compareTo(s.getCashBookBalance()));
        assertEquals(0, new BigDecimal("1500").compareTo(s.getOutstandingDeposits()));
        assertEquals(0, s.getDifference().signum());
        assertThrows(IllegalStateException.class, () -> item(id, "OUTSTANDING_DEPOSIT", "5", oct30, null));
        assertThrows(IllegalStateException.class, () -> service.updateStatement(firm, id, new UpdateStatementRequest(BigDecimal.ONE, null)));
        assertThrows(IllegalStateException.class, () -> service.submit(firm, id, preparer));
    }

    // ── returning ────────────────────────────────────────────────────────────

    @Test
    void returningNeedsAReasonAndReopensTheDraft() {
        UUID id = balancedAndSubmitted();
        assertThrows(IllegalArgumentException.class, () -> service.returnToDraft(firm, id, "no", "Boss"));
        var back = service.returnToDraft(firm, id, "The statement balance is for the wrong day", "Boss");
        assertEquals("DRAFT", back.status());
        assertEquals("The statement balance is for the wrong day", back.returnReason());
        assertNull(recons.get(id).getCashBookBalance(), "stale frozen figures are cleared");
        service.updateStatement(firm, id, new UpdateStatementRequest(new BigDecimal("8000.00"), null));
        assertNotNull(service.submit(firm, id, preparer));
    }

    @Test
    void aDraftCannotBeReturnedAndAnApprovedOneCannotEither() {
        var r = start("0");
        assertThrows(IllegalStateException.class, () -> service.returnToDraft(firm, r.id(), "Some good reason here", "Boss"));
        UUID id = approvedSetup();
        assertThrows(IllegalStateException.class, () -> service.returnToDraft(firm, id, "Some good reason here", "Boss"));
    }

    private UUID approvedSetup() {
        recons.clear(); items.clear(); ledger.clear();
        UUID id = balancedAndSubmitted();
        service.approve(firm, id, null, approver, "Boss");
        return id;
    }

    // ── approving ────────────────────────────────────────────────────────────

    @Test
    void theSubmitterCannotApproveUnlessTheFirmHasAllowedSelfApproval() {
        UUID id = balancedAndSubmitted();
        assertThrows(IllegalStateException.class, () -> service.approve(firm, id, null, preparer, "Clerk"));
        assertEquals("SUBMITTED", recons.get(id).getStatus());
        settings.update(firm, true, "Sole practitioner: there is nobody else", approver, "Boss");
        var r = service.approve(firm, id, null, preparer, "Clerk");
        assertEquals("APPROVED", r.status());
        assertTrue(r.selfApproved(), "recorded as a self-approval");
    }

    @Test
    void aDifferentPersonApprovesAndThePeriodIsLocked() {
        assertNull(service.lock(firm).lockedThrough());
        UUID id = balancedAndSubmitted();
        var r = service.approve(firm, id, new ApproveReconciliationRequest("Checked against the statement"), approver, "Boss");
        assertEquals("APPROVED", r.status());
        assertFalse(r.selfApproved());
        assertFalse(r.live());
        assertEquals("Boss", r.approvedByName());
        assertEquals(oct31, service.lock(firm).lockedThrough());
        assertEquals(2, r.items().size(), "the items are kept with the approved record");
        assertThrows(IllegalStateException.class, () -> service.approve(firm, id, null, approver, "Boss"));
        assertThrows(IllegalStateException.class, () -> item(id, "OUTSTANDING_DEPOSIT", "5", oct30, null));
    }

    @Test
    void oncePeriodIsLockedNothingUpToThatDateCanBeReconciledAgain() {
        approvedSetup();
        assertThrows(IllegalStateException.class, () -> start("0"));
        assertThrows(IllegalStateException.class, () -> service.start(firm, new StartReconciliationRequest(oct30, BigDecimal.ZERO, null), preparer, "Clerk"));
        assertNotNull(service.start(firm, new StartReconciliationRequest(LocalDate.now(), BigDecimal.ZERO, null), preparer, "Clerk"));
    }

    @Test
    void aLedgerEntryPostedAfterSubmissionBlocksApprovalUntilItIsReturned() {
        UUID id = balancedAndSubmitted();
        receipt(firm, clientA, "250", oct29);
        assertTrue(service.get(firm, id).changedSinceSubmitted());
        var ex = assertThrows(IllegalStateException.class, () -> service.approve(firm, id, null, approver, "Boss"));
        assertTrue(ex.getMessage().contains("changed"), ex.getMessage());
        assertEquals("SUBMITTED", recons.get(id).getStatus());
        service.returnToDraft(firm, id, "A receipt was posted after submission", "Boss");
        assertThrows(IllegalStateException.class, () -> service.submit(firm, id, preparer), "now 250 is unexplained");
    }

    @Test
    void anotherFirmCannotSeeOrApprove() {
        UUID id = balancedAndSubmitted();
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherFirm, id));
        assertThrows(ResourceNotFoundException.class, () -> service.approve(otherFirm, id, null, approver, "X"));
        assertNull(service.lock(otherFirm).lockedThrough());
    }

    // ── the third check: client records against the ledger ───────────────────

    @Test
    void aClientRecordThatDisagreesWithTheLedgerNeedsAnExplanationToApprove() {
        stored.put(clientA, new Object[]{clientA, "Dlamini", new BigDecimal("8800.00")});
        UUID id = balancedAndSubmitted();
        var view = service.get(firm, id);
        assertEquals(1, view.ledgerMismatchCount());
        assertEquals("Dlamini", view.ledgerMismatches().get(0).clientName());
        assertEquals(0, new BigDecimal("8800").compareTo(view.ledgerMismatches().get(0).clientRecord()));
        assertEquals(0, new BigDecimal("9000").compareTo(view.ledgerMismatches().get(0).ledger()));
        assertThrows(IllegalArgumentException.class, () -> service.approve(firm, id, null, approver, "Boss"));
        assertThrows(IllegalArgumentException.class, () -> service.approve(firm, id, new ApproveReconciliationRequest("ok"), approver, "Boss"));
        var done = service.approve(firm, id, new ApproveReconciliationRequest("Client record is behind a pending refund; checked"), approver, "Boss");
        assertEquals("APPROVED", done.status());
        assertEquals(1, done.ledgerMismatchCount(), "the count is kept with the approved record");
    }

    @Test
    void aClientMissingFromTheRecordsOrWithNoLedgerIsAMismatchToo() {
        stored.put(clientB, new Object[]{clientB, "Nkosi", new BigDecimal("40.00")});
        october();
        var r = start("8000");
        assertEquals(1, r.ledgerMismatchCount());
        assertEquals("Nkosi", r.ledgerMismatches().get(0).clientName());
        stored.remove(clientA);
        assertEquals(2, service.get(firm, r.id()).ledgerMismatchCount(), "the ledger holds 9,000 for a client the records do not have");
    }

    @Test
    void aClientWhoseLedgerWentNegativeIsReported() {
        october();
        payment(firm, clientB, "300", oct20);
        stored.put(clientB, new Object[]{clientB, "Nkosi", new BigDecimal("-300.00")});
        var r = start("8000");
        assertEquals(1, r.negativeClients().size());
        assertEquals("Nkosi", r.negativeClients().get(0).clientName());
        assertEquals(0, new BigDecimal("-300").compareTo(r.negativeClients().get(0).amount()));
    }

    // ── listing ──────────────────────────────────────────────────────────────

    @Test
    void theStatementBalanceCanBeCorrectedWhileInDraft() {
        october();
        var r = start("7000");
        var fixed = service.updateStatement(firm, r.id(), new UpdateStatementRequest(new BigDecimal("8000"), "From the October statement"));
        assertEquals(0, new BigDecimal("8000").compareTo(fixed.statementBalance()));
        assertEquals("From the October statement", fixed.statementNote());
        assertEquals(0, new BigDecimal("1000").compareTo(fixed.difference()));
    }
}
