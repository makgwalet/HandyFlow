package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseBillingApprovalTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();

    private WhseBillingInvoiceRepository invoices;
    private WhseStorageAccrualRepository accruals;
    private SeparationOfDutiesChecker duties;
    private AccountingFacade accounting;
    private WhseBillingService service;
    private WhseBillingInvoice invoice;

    @BeforeEach
    void setUp() {
        invoices = mock(WhseBillingInvoiceRepository.class);
        accruals = mock(WhseStorageAccrualRepository.class);
        duties = mock(SeparationOfDutiesChecker.class);
        accounting = mock(AccountingFacade.class);
        service = new WhseBillingService(invoices, mock(WhseInventoryRepository.class), accruals, mock(WhseItemRepository.class),
                mock(WhseInboundShipmentLineRepository.class), mock(WhseOutboundOrderRepository.class),
                mock(WhseOutboundOrderLineRepository.class), mock(WhseClientService.class), mock(WhseProfileService.class),
                mock(WhseNumberGenerator.class), accounting, duties, mock(WhseTariffService.class));
        invoice = WhseBillingInvoice.create(tenant, client, "WHI00001", LocalDate.now().minusMonths(1), LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("300"), new BigDecimal("100"), BigDecimal.ZERO);
        invoice.preparedBy(maker);
        when(invoices.findByTenantAndId(tenant, invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoices.save(any(WhseBillingInvoice.class))).thenAnswer(i -> i.getArgument(0));
        when(invoices.saveAndFlush(any(WhseBillingInvoice.class))).thenAnswer(i -> i.getArgument(0));
        when(accounting.getAccounts(any())).thenReturn(java.util.List.of());
    }

    private TenantId t() { return TenantId.of(tenant); }

    @Test
    @DisplayName("a second person's approval issues the invoice and records who approved")
    void approvalIssues() {
        when(duties.otherHolders(any(), anyString(), any())).thenReturn(1L);

        WhseBillingInvoice out = service.approveInvoice(t(), invoice.getId(), "checked", checker);

        assertEquals("SENT", out.getStatus());
        assertEquals(checker, out.getDecidedBy());
        assertFalse(out.isSoleException());
    }

    @Test
    @DisplayName("the preparer cannot approve while someone else can; the invoice stays a draft")
    void preparerBlocked() {
        doThrow(new HandyFlowException("same person", HttpStatus.CONFLICT, "SAME_PERSON"))
                .when(duties).require(any(), eq("WAREHOUSING_BILLING_APPROVE"), eq(maker), eq(maker), anyString(), anyString(), any(), anyString());

        assertThrows(HandyFlowException.class, () -> service.approveInvoice(t(), invoice.getId(), null, maker));
        assertEquals("DRAFT", invoice.getStatus());
        verify(invoices, never()).save(any());
    }

    @Test
    @DisplayName("when nobody else can approve, the preparer's approval is allowed and flagged as the sole exception")
    void soleExceptionFlagged() {
        when(duties.otherHolders(any(), eq("WAREHOUSING_BILLING_APPROVE"), eq(maker))).thenReturn(0L);

        WhseBillingInvoice out = service.approveInvoice(t(), invoice.getId(), null, maker);

        assertEquals("SENT", out.getStatus());
        assertTrue(out.isSoleException());
    }

    @Test
    @DisplayName("an invoice that has been decided cannot be approved or rejected again")
    void decidedOnce() {
        invoice.reject(checker, "wrong period");
        assertThrows(HandyFlowException.class, () -> service.approveInvoice(t(), invoice.getId(), null, checker));
        assertThrows(HandyFlowException.class, () -> service.rejectInvoice(t(), invoice.getId(), "wrong again", checker));
    }

    @Test
    @DisplayName("rejecting needs a reason, marks the invoice REJECTED and gives the storage days back")
    void rejectReleasesAccruals() {
        assertThrows(IllegalArgumentException.class, () -> service.rejectInvoice(t(), invoice.getId(), "no", checker));

        WhseBillingInvoice out = service.rejectInvoice(t(), invoice.getId(), "wrong period", checker);

        assertEquals("REJECTED", out.getStatus());
        verify(accruals).release(invoice.getId());
    }

    @Test
    @DisplayName("a second invoice cannot be prepared while one waits for approval")
    void oneDraftAtATime() {
        when(invoices.countAwaitingApproval(tenant, client)).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.generateInvoice(t(), client, LocalDate.now(), maker));
    }
}
