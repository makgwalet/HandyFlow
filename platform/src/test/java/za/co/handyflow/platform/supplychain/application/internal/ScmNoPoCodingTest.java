package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.enums.MatchStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierInvoice;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierInvoiceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** An invoice with no PO reaches a budget only through its coding, so approval is refused while it is uncoded and budgets control spending. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScmNoPoCodingTest {

    private final UUID tenant = UUID.randomUUID();
    private final TenantId tid = TenantId.of(tenant);

    @Mock ScSupplierInvoiceRepository invoiceRepo;
    @Mock InvoiceMatchingService matchingService;
    @Mock BudgetService budgetService;
    @InjectMocks ScmService service;

    private ScSupplierInvoice noPo;

    @BeforeEach
    void setUp() {
        noPo = ScSupplierInvoice.create(tenant, "SINV-9", UUID.randomUUID(), null, null, "REF-9", LocalDate.now(), LocalDate.now().plusDays(30), "ZAR",
                new BigDecimal("100"), new BigDecimal("15"), new BigDecimal("115"), null, UUID.randomUUID());
        noPo.applyMatchOutcome(MatchStatus.MATCHED, "ok");
        when(invoiceRepo.findByTenantIdAndId(eq(tenant), any())).thenReturn(Optional.of(noPo));
        when(invoiceRepo.save(any(ScSupplierInvoice.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void anUncodedNoPoInvoiceCannotBeApprovedWhileBudgetsControlSpending() {
        when(budgetService.hasActiveBudgets(tid)).thenReturn(true);
        assertThatThrownBy(() -> service.approveSupplierInvoice(tid, UUID.randomUUID(), UUID.randomUUID(), "Approver"))
                .isInstanceOf(HandyFlowException.class).extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("CODING_REQUIRED");
        assertThat(noPo.getStatus()).isNotEqualTo(InvoiceStatus.APPROVED);
    }

    @Test
    void codingASingleDimensionIsEnoughAndCanBeAddedBeforeApproval() {
        ScSupplierInvoice coded = service.codeSupplierInvoice(tid, UUID.randomUUID(), "Workshop", null, "  ");
        assertThat(coded.getDepartment()).isEqualTo("Workshop");
        assertThat(coded.getCostCentre()).isNull();
        assertThat(coded.getProjectRef()).isNull();
    }

    @Test
    void anApprovedInvoiceCannotBeRecoded() {
        noPo.approve(UUID.randomUUID(), "Someone");
        assertThatThrownBy(() -> service.codeSupplierInvoice(tid, UUID.randomUUID(), "Stores", null, null))
                .isInstanceOf(HandyFlowException.class).extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("INVOICE_LOCKED");
    }

    @Test
    void anInvoiceOnAPurchaseOrderCannotBeCodedHere() {
        ScSupplierInvoice onPo = ScSupplierInvoice.create(tenant, "SINV-10", UUID.randomUUID(), UUID.randomUUID(), null, "REF-10", LocalDate.now(), LocalDate.now().plusDays(30), "ZAR",
                new BigDecimal("100"), new BigDecimal("15"), new BigDecimal("115"), null, UUID.randomUUID());
        when(invoiceRepo.findByTenantIdAndId(eq(tenant), any())).thenReturn(Optional.of(onPo));
        assertThatThrownBy(() -> service.codeSupplierInvoice(tid, UUID.randomUUID(), "Stores", null, null))
                .isInstanceOf(HandyFlowException.class).extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("HAS_PO");
    }
}
