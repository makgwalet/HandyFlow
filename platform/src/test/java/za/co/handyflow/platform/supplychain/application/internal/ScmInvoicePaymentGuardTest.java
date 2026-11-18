package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.ap.application.ApFacade;
import za.co.handyflow.platform.ap.domain.model.ApBill;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.enums.MatchExceptionType;
import za.co.handyflow.platform.supplychain.domain.enums.MatchStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierInvoiceRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Proves the rule "an invoice with an open critical match exception cannot be approved or paid":
 * every transition is tested for every critical exception type, and a source scan shows the three transitions that
 * move an invoice towards payment are reachable only through ScmService (where the guard lives).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScmInvoicePaymentGuardTest {

    private final UUID tenant = UUID.randomUUID();
    private final TenantId tid = TenantId.of(tenant);
    private final UUID approver = UUID.randomUUID();

    @Mock ScSupplierInvoiceRepository invoiceRepo;
    @Mock InvoiceMatchingService matchingService;
    @Mock ControlExceptionFacade controlExceptionFacade;
    @Mock ApFacade apFacade;
    @Mock ScSupplierRepository supplierRepo;
    @Mock ScmSeparationOfDuties sod;
    @Mock ApprovalLimitService approvalLimits;
    @Mock ProcurementPolicy procurementPolicy;
    @InjectMocks ScmService service;

    private ScSupplierInvoice invoice;

    @BeforeEach
    void setUp() {
        invoice = ScSupplierInvoice.create(tenant, "SINV-1", UUID.randomUUID(), UUID.randomUUID(), null, "REF-1",
                LocalDate.now(), LocalDate.now().plusDays(30), "ZAR", new BigDecimal("100"), new BigDecimal("15"),
                new BigDecimal("115"), null, UUID.randomUUID());
        when(invoiceRepo.findByTenantIdAndId(eq(tenant), any())).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(ScSupplierInvoice.class))).thenAnswer(i -> i.getArgument(0));
        when(supplierRepo.findByTenantIdAndId(eq(tenant), any())).thenReturn(Optional.empty());
        ApBill bill = mock(ApBill.class);
        UUID billId = UUID.randomUUID();
        when(bill.getId()).thenReturn(billId);
        when(apFacade.createBillFromSupplyChainInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(bill);
    }

    private void openCritical(long n) {
        when(matchingService.openBlockingExceptionCount(eq(tid), any())).thenReturn(n);
    }

    @ParameterizedTest
    @EnumSource(MatchExceptionType.class)
    void everyCriticalTypeIsBlocking_andNoOtherTypeIs(MatchExceptionType t) {
        assertThat(t.blocksPayment()).isEqualTo(t.resultingStatus() == MatchStatus.DISPUTE);
        if (t.resultingStatus() == MatchStatus.DISPUTE) assertThat(MatchExceptionType.blocking()).contains(t);
        else assertThat(MatchExceptionType.blocking()).doesNotContain(t);
        assertThat(MatchExceptionType.blocking()).hasSize(
                (int) Stream.of(MatchExceptionType.values()).filter(x -> x.resultingStatus() == MatchStatus.DISPUTE).count());
    }

    @Test
    void approve_isRefusedWhileACriticalExceptionIsOpen_evenIfMatchStatusSaysMatched() {
        invoice.applyMatchOutcome(MatchStatus.MATCHED, "ok");          // the summary field says fine ...
        openCritical(1);                                               // ... the exception rows say otherwise
        assertThatThrownBy(() -> service.approveSupplierInvoice(tid, invoice.getId(), approver, "Approver"))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("CRITICAL_EXCEPTION_OPEN");
        assertThat(invoice.getStatus()).isNotEqualTo(InvoiceStatus.APPROVED);
        verifyNoInteractions(apFacade);                                // and nothing reached AP
    }

    @Test
    void approve_goesThroughWhenNoCriticalExceptionIsOpen() {
        invoice.applyMatchOutcome(MatchStatus.MATCHED, "ok");
        openCritical(0);
        service.approveSupplierInvoice(tid, invoice.getId(), approver, "Approver");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        verify(apFacade).createBillFromSupplyChainInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void markPaid_isRefusedWhileACriticalExceptionIsOpen() {
        invoice.applyMatchOutcome(MatchStatus.MATCHED, "ok");
        invoice.approve(approver, "Approver");                        // legacy invoice: approved, never handed to AP
        openCritical(2);
        assertThatThrownBy(() -> service.markPaid(tid, invoice.getId(), "PAY-1"))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("CRITICAL_EXCEPTION_OPEN");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }

    @Test
    void markPaid_stillRefusedForAnInvoiceHandedOffToAp() {
        invoice.applyMatchOutcome(MatchStatus.MATCHED, "ok");
        invoice.approve(approver, "Approver");
        invoice.recordApBillHandOff(UUID.randomUUID());
        openCritical(0);
        assertThatThrownBy(() -> service.markPaid(tid, invoice.getId(), "PAY-1"))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("ALREADY_HANDED_OFF");
    }

    @Test
    void override_requiresAReason_resolvesTheExceptions_andHandsOffOnce() {
        invoice.applyMatchOutcome(MatchStatus.DISPUTE, "price variance");
        assertThatThrownBy(() -> service.overrideDisputedInvoice(tid, invoice.getId(), approver, "Approver", " "))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("REASON_REQUIRED");
        service.overrideDisputedInvoice(tid, invoice.getId(), approver, "Approver", "Supplier credit note agreed");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(invoice.getMatchStatus()).isEqualTo(MatchStatus.OVERRIDDEN);
        verify(matchingService).resolveOpenExceptions(tid, invoice.getId());
        verify(apFacade, times(1)).createBillFromSupplyChainInvoice(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancel_isRefusedOnceTheInvoiceIsAnApBill() {
        invoice.applyMatchOutcome(MatchStatus.MATCHED, "ok");
        invoice.approve(approver, "Approver");
        invoice.recordApBillHandOff(UUID.randomUUID());
        assertThatThrownBy(() -> service.cancelSupplierInvoice(tid, invoice.getId(), "mistake"))
                .isInstanceOf(HandyFlowException.class)
                .extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("ALREADY_HANDED_OFF");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }

    @Test
    void aMatchRunCannotRewriteAnApprovedInvoice() {
        invoice.applyMatchOutcome(MatchStatus.MATCHED, "ok");
        invoice.approve(approver, "Approver");
        assertThatThrownBy(() -> invoice.applyMatchOutcome(MatchStatus.MATCHED, "again")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> invoice.dispute("x")).isInstanceOf(IllegalStateException.class);  // would strand a payable AP bill
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
        invoice.recordApBillHandOff(UUID.randomUUID());
        assertThatThrownBy(() -> invoice.markPaid("P")).isInstanceOf(IllegalStateException.class);   // payable only through AP now
    }

    /** The only code allowed to call the three transitions towards payment is ScmService (guarded) and the entity itself. */
    @Test
    void onlyScmServiceMovesAnInvoiceTowardsPayment() throws IOException {
        Path root = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            var offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.endsWith("ScmService.java") && !p.endsWith("ScSupplierInvoice.java"))
                    .filter(p -> {
                        try {
                            String s = Files.readString(p);
                            return s.contains("overrideDisputeAndApprove(") || s.contains("inv.markPaid(") || s.contains("invoice.markPaid(")
                                    || (s.contains("ScSupplierInvoice") && (s.contains(".approve(") && s.contains("invoiceRepo")));
                        } catch (IOException e) { throw new RuntimeException(e); }
                    }).map(Path::toString).toList();
            assertThat(offenders).as("files that bypass the ScmService guard").isEmpty();
        }
    }
}
