package za.co.handyflow.platform.fuel.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelImportKey;
import za.co.handyflow.platform.fuel.domain.model.FuelPosImport;
import za.co.handyflow.platform.fuel.domain.model.FuelPosTransaction;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportKeyRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosImportRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosTransactionRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.AdjustmentRequest;
import za.co.handyflow.platform.fuel.dto.ForecourtDtos.AdjustmentView;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Live pump transactions: key rules, idempotency, refunds and voids against their sales, and the hand-over to shift adjustments on accept. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelPosTransactionServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static final String SRC = "ACME-POS", SHIFT = "S-1";

    @Mock FuelImportKeyRepository keys;
    @Mock FuelPosTransactionRepository txs;
    @Mock FuelPosImportRepository imports;
    @Mock FuelCashControlService cash;
    @Mock FuelDivisionGuard divisions;
    @Mock FeatureGuard featureGuard;

    FuelPosTransactionService svc;
    String raw = "hf_fuel_testkey";
    List<FuelPosTransaction> store;

    @BeforeEach void setUp() {
        svc = new FuelPosTransactionService(keys, txs, imports, cash, divisions, featureGuard, new ObjectMapper().findAndRegisterModules());
        FuelImportKey key = FuelImportKey.create(T, "Site POS", FuelPosImportService.hashKey(raw), raw.substring(0, 12), USER);
        when(keys.findByKeyHash(FuelPosImportService.hashKey(raw))).thenReturn(Optional.of(key));
        when(featureGuard.hasModuleFor(T, "fuel")).thenReturn(true);
        store = new ArrayList<>();
        when(txs.save(any(FuelPosTransaction.class))).thenAnswer(i -> { FuelPosTransaction t = i.getArgument(0); if (!store.contains(t)) store.add(t); return t; });
        when(txs.findByRef(eq(T), any(), any())).thenAnswer(i -> store.stream().filter(t -> t.getSource().equals(i.getArgument(1)) && t.getExternalId().equals(i.getArgument(2))).findFirst());
        when(txs.findByShiftRef(eq(T), any(), any())).thenAnswer(i -> store.stream().filter(t -> t.getSource().equals(i.getArgument(1)) && t.getShiftRef().equals(i.getArgument(2))).toList());
        when(imports.findByRef(any(), any(), any())).thenReturn(Optional.empty());
    }

    static BigDecimal d(String s) { return new BigDecimal(s); }

    static IncomingTransaction sale(String id, String amount, String litres) { return new IncomingTransaction(id, "SALE", null, "Pump 1", d(litres), d(amount), "CASH", null, null); }
    static IncomingTransaction refund(String id, String orig, String amount) { return new IncomingTransaction(id, "REFUND", null, null, null, d(amount), "CASH", orig, "Customer returned fuel"); }
    static IncomingTransaction voided(String id, String orig, String amount) { return new IncomingTransaction(id, "VOID", null, null, null, d(amount), "CASH", orig, "Keyed on the wrong pump"); }
    static IncomingTransactions batch(IncomingTransaction... t) { return new IncomingTransactions(SRC, SHIFT, List.of(t)); }

    // ── key and licence ──────────────────────────────────────────────────────

    @Test @DisplayName("a missing, unknown or wrong key is refused with 401 and nothing is stored")
    void badKeys() {
        for (String k : new String[]{null, "", "hf_fuel_wrong"}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> svc.ingest(k, batch(sale("T1", "100", "5"))));
            assertEquals(401, ex.getStatusCode().value());
        }
        verify(txs, never()).save(any());
    }

    // ── ingest ───────────────────────────────────────────────────────────────

    @Test @DisplayName("sales are stored against the shift reference and answered STAGED, in the sender's order")
    void stagesSales() {
        TransactionIngestResult r = svc.ingest(raw, batch(sale("T2", "221", "10"), sale("T1", "110.50", "5")));
        assertEquals(List.of("T2", "T1"), r.results().stream().map(TransactionOutcome::externalId).toList());
        assertTrue(r.results().stream().allMatch(o -> "STAGED".equals(o.outcome()) && o.problems().isEmpty()));
        assertEquals(2, store.size());
        assertEquals(SHIFT, store.get(0).getShiftRef());
    }

    @Test @DisplayName("sending the same transaction again is a DUPLICATE and stores nothing new; 10 and 10.00 are the same")
    void duplicate() {
        svc.ingest(raw, batch(sale("T1", "100", "5")));
        TransactionIngestResult r = svc.ingest(raw, batch(sale("T1", "100.00", "5.000")));
        assertEquals("DUPLICATE", r.results().get(0).outcome());
        assertEquals(1, store.size());
    }

    @Test @DisplayName("the same externalId with different content is a CONFLICT and is not overwritten")
    void conflict() {
        svc.ingest(raw, batch(sale("T1", "100", "5")));
        TransactionIngestResult r = svc.ingest(raw, batch(sale("T1", "150", "5")));
        assertEquals("CONFLICT", r.results().get(0).outcome());
        assertEquals(0, d("100").compareTo(store.get(0).getAmount()));
    }

    @Test @DisplayName("a refund sent in the same request as its sale has no problem")
    void refundWithSaleSameBatch() {
        TransactionIngestResult r = svc.ingest(raw, batch(sale("T1", "100", "5"), refund("R1", "T1", "40")));
        assertTrue(r.results().get(1).problems().isEmpty());
    }

    @Test @DisplayName("a refund for a sale not received yet is stored with a problem listed, and clears when the sale arrives")
    void refundBeforeSale() {
        TransactionIngestResult r = svc.ingest(raw, batch(refund("R1", "T1", "40")));
        assertEquals("STAGED", r.results().get(0).outcome());
        assertTrue(r.results().get(0).problems().get(0).contains("has not been received"));
        svc.ingest(raw, batch(sale("T1", "100", "5")));
        assertTrue(FuelPosTransactionService.problemsByRow(store).isEmpty());
    }

    @Test @DisplayName("refunds and voids on one sale may not add up to more than the sale")
    void cannotExceedSale() {
        TransactionIngestResult r = svc.ingest(raw, batch(sale("T1", "100", "5"), refund("R1", "T1", "70"), refund("R2", "T1", "40")));
        assertTrue(r.results().get(1).problems().isEmpty());
        assertTrue(r.results().get(2).problems().get(0).contains("more than the sale"));
    }

    @Test @DisplayName("a void must equal the whole sale")
    void voidMustBeWhole() {
        TransactionIngestResult r = svc.ingest(raw, batch(sale("T1", "100", "5"), voided("V1", "T1", "60")));
        assertTrue(r.results().get(1).problems().stream().anyMatch(p -> p.contains("must equal the whole sale")));
    }

    @Test @DisplayName("transactions for a shift already accepted are kept as a record only, with the reason")
    void lateForAcceptedShift() {
        FuelPosImport done = FuelPosImport.receive(T, null, SRC, SHIFT, "{}", "h", null);
        done.accept(USER, UUID.randomUUID(), null);
        when(imports.findByRef(T, SRC, SHIFT)).thenReturn(Optional.of(done));
        TransactionIngestResult r = svc.ingest(raw, batch(sale("T1", "100", "5")));
        assertTrue(r.results().get(0).problems().get(0).contains("already accepted"));
        assertEquals(FuelPosTransaction.NOT_APPLIED, store.get(0).getStatus());
    }

    // ── shape ────────────────────────────────────────────────────────────────

    @Test @DisplayName("shape problems are refused with nothing stored")
    void shape() {
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, batch(sale("T1", "100", "5"), sale("T1", "50", "2")))); // twice
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, batch(new IncomingTransaction("T1", "SALE", null, null, d("5"), d("100"), "CASH", null, null)))); // no pump
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, batch(new IncomingTransaction("T1", "SALE", null, "Pump 1", null, d("100"), "CASH", null, null)))); // no litres
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, batch(new IncomingTransaction("R1", "REFUND", null, null, null, d("5"), "CASH", null, "A good reason")))); // no original
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, batch(new IncomingTransaction("R1", "REFUND", null, null, null, d("5"), "CASH", "T1", "bad")))); // reason too short
        assertThrows(IllegalArgumentException.class, () -> svc.ingest(raw, batch(new IncomingTransaction("R1", "REFUND", null, null, null, d("5"), "CASH", "R1", "A good reason")))); // itself
        verify(txs, never()).save(any());
    }

    // ── apply on accept ──────────────────────────────────────────────────────

    @Test @DisplayName("accept turns each refund and void into an approved adjustment by the accepting supervisor, and marks the rows applied")
    void applies() {
        svc.ingest(raw, batch(sale("T1", "100", "5"), refund("R1", "T1", "40"), sale("T2", "50", "2"), voided("V1", "T2", "50")));
        UUID shiftId = UUID.randomUUID();
        when(cash.record(eq(T), eq(shiftId), any(AdjustmentRequest.class), isNull())).thenAnswer(i -> {
            AdjustmentRequest q = i.getArgument(2);
            return new AdjustmentView(UUID.randomUUID(), shiftId, q.kind(), q.amount(), q.reason(), "PENDING", null, Instant.now(), null, null, null);
        });
        int n = svc.applyToShift(T, SRC, SHIFT, shiftId, USER);
        assertEquals(2, n);
        ArgumentCaptor<AdjustmentRequest> cap = ArgumentCaptor.forClass(AdjustmentRequest.class);
        verify(cash, times(2)).record(eq(T), eq(shiftId), cap.capture(), isNull());
        assertEquals(List.of("REFUND", "VOID"), cap.getAllValues().stream().map(AdjustmentRequest::kind).toList());
        assertEquals(0, d("40").compareTo(cap.getAllValues().get(0).amount()));
        assertTrue(cap.getAllValues().get(0).reason().contains("sale T1"));
        verify(cash, times(2)).approve(eq(T), any(UUID.class), any(), eq(USER));
        assertTrue(store.stream().allMatch(t -> FuelPosTransaction.APPLIED.equals(t.getStatus())));
        assertNotNull(store.get(1).getAdjustmentId());
        assertNull(store.get(0).getAdjustmentId());
    }

    @Test @DisplayName("a reason that would be too long is cut to the adjustment limit")
    void longReasonCut() {
        String longReason = "x".repeat(300);
        svc.ingest(raw, batch(sale("T1", "100", "5"), new IncomingTransaction("R1", "REFUND", null, null, null, d("5"), "CASH", "T1", longReason)));
        UUID shiftId = UUID.randomUUID();
        when(cash.record(eq(T), eq(shiftId), any(AdjustmentRequest.class), isNull())).thenReturn(new AdjustmentView(UUID.randomUUID(), shiftId, "REFUND", d("5"), "r", "PENDING", null, Instant.now(), null, null, null));
        svc.applyToShift(T, SRC, SHIFT, shiftId, USER);
        ArgumentCaptor<AdjustmentRequest> cap = ArgumentCaptor.forClass(AdjustmentRequest.class);
        verify(cash).record(eq(T), eq(shiftId), cap.capture(), isNull());
        assertEquals(300, cap.getValue().reason().length());
    }

    @Test @DisplayName("accept is refused, and nothing applied, when a refund does not add up")
    void refusesBadRefund() {
        svc.ingest(raw, batch(refund("R1", "T9", "40")));
        assertThrows(IllegalArgumentException.class, () -> svc.applyToShift(T, SRC, SHIFT, UUID.randomUUID(), USER));
        verify(cash, never()).record(any(), any(), any(), any());
        assertEquals(FuelPosTransaction.RECEIVED, store.get(0).getStatus());
    }

    @Test @DisplayName("the automatic accept (no person) is refused when the shift carries a refund or void, but is fine with sales only")
    void automaticAcceptNeedsAPerson() {
        svc.ingest(raw, batch(sale("T1", "100", "5"), refund("R1", "T1", "40")));
        assertThrows(IllegalStateException.class, () -> svc.applyToShift(T, SRC, SHIFT, UUID.randomUUID(), null));
        store.clear();
        svc.ingest(raw, batch(sale("T1", "100", "5")));
        assertEquals(0, svc.applyToShift(T, SRC, SHIFT, UUID.randomUUID(), null));
        assertEquals(FuelPosTransaction.APPLIED, store.get(0).getStatus());
    }

    @Test @DisplayName("a shift with no live transactions accepts exactly as before")
    void noTransactions() {
        assertEquals(0, svc.applyToShift(T, SRC, SHIFT, UUID.randomUUID(), USER));
        verifyNoInteractions(cash);
    }

    @Test @DisplayName("rejecting the shift marks its waiting transactions not applied")
    void rejectMarks() {
        svc.ingest(raw, batch(sale("T1", "100", "5")));
        svc.markNotApplied(T, SRC, SHIFT);
        assertEquals(FuelPosTransaction.NOT_APPLIED, store.get(0).getStatus());
        assertTrue(store.get(0).getProblems().contains("rejected"));
    }

    // ── summary ──────────────────────────────────────────────────────────────

    @Test @DisplayName("the summary nets refunds and voids per tender and shows the litres difference against the meters")
    void summary() {
        List<FuelPosTransaction> rows = List.of(
                row("T1", FuelPosTransaction.SALE, "100", "5", "CASH", null),
                row("T2", FuelPosTransaction.SALE, "200", "10", "CARD", null),
                row("R1", FuelPosTransaction.REFUND, "30", null, "CASH", "T1"),
                row("V1", FuelPosTransaction.VOID, "200", null, "CARD", "T2"));
        TransactionSummary s = FuelPosTransactionService.summarise(rows, d("16"));
        assertEquals(2, s.sales());
        assertEquals(0, d("15").compareTo(s.salesLitres()));
        assertEquals(0, d("300").compareTo(s.salesAmount()));
        assertEquals(0, d("30").compareTo(s.refundsAmount()));
        assertEquals(0, d("200").compareTo(s.voidsAmount()));
        assertEquals(0, d("70").compareTo(s.netAmount()));
        assertEquals(0, d("70").compareTo(s.netByTender().get("CASH")));
        assertEquals(0, BigDecimal.ZERO.compareTo(s.netByTender().get("CARD")));
        assertEquals(0, d("-1").compareTo(s.litresDifference()));
    }

    @Test @DisplayName("without a staged shift there is no litres difference, and rows not applied are counted apart")
    void summaryNoMeters() {
        FuelPosTransaction late = row("T3", FuelPosTransaction.SALE, "10", "1", "CASH", null);
        late.notApplied("late");
        TransactionSummary s = FuelPosTransactionService.summarise(List.of(row("T1", FuelPosTransaction.SALE, "100", "5", "CASH", null), late), null);
        assertNull(s.litresDifference());
        assertEquals(1, s.sales());
        assertEquals(1, s.notApplied());
    }

    private static FuelPosTransaction row(String id, String kind, String amount, String litres, String tender, String orig) {
        return FuelPosTransaction.receive(T, null, SRC, SHIFT, id, kind, orig, "Pump 1", litres == null ? null : d(litres), d(amount), tender, null,
                orig == null ? null : "Reason given", "h-" + id);
    }
}
