package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.BillingDtos.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ComplianceBillingServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceAgreementRepository agreements;
    @Mock ComplianceBillingEventRepository events;
    @Mock InvoicingFacade invoicing;

    private ComplianceBillingService service;
    private ComplianceClient client;
    private ComplianceAgreement agreement;

    @BeforeEach
    void setUp() {
        service = new ComplianceBillingService(clients, agreements, events, invoicing);
        client = ComplianceClient.create(TENANT, "Acme", CUSTOMER, null, null, null, ME);
        agreement = ComplianceAgreement.draft(TENANT, client.getId(), UUID.randomUUID(), "CA-000001", new BigDecimal("18000"), new BigDecimal("20700"), "ZAR",
                new ComplianceAgreement.Terms("Annual", LocalDate.of(2027, 3, 1), null, "MONTHLY", new BigDecimal("1500"), LocalDate.of(2027, 3, 1), false, 30, null, null), ME);
        agreement.activate(ME, "Signed copy returned by the director");
        lenient().when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        lenient().when(agreements.findOne(TENANT, agreement.getId())).thenReturn(Optional.of(agreement));
        lenient().when(events.findForAgreement(TENANT, agreement.getId())).thenReturn(List.of());
        lenient().when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(true, new BigDecimal("15"), true, LocalDate.of(2020, 1, 1)));
    }

    private ComplianceBillingEvent event(LocalDate due) {
        ComplianceBillingEvent e = ComplianceBillingEvent.schedule(TENANT, agreement, due);
        lenient().when(events.findOne(TENANT, e.getId())).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    @DisplayName("Generating makes the missing bills inside the horizon, past ones included")
    void generates() {
        Generated g = service.generate(TENANT, client.getId(), agreement.getId(), TODAY);
        ArgumentCaptor<List<ComplianceBillingEvent>> saved = ArgumentCaptor.forClass(List.class);
        verify(events).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(g.created());
        assertThat(g.created()).isEqualTo(3);
    }

    @Test
    @DisplayName("Only an active agreement can generate bills")
    void notActive() {
        agreement.suspend("Client asked for a pause for now");
        assertThatThrownBy(() -> service.generate(TENANT, client.getId(), agreement.getId(), TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("active");
    }

    @Test
    @DisplayName("Raising makes a draft invoice with the VAT rate on the due date and records it on the bill")
    void raises() {
        ComplianceBillingEvent e = event(TODAY);
        UUID inv = UUID.randomUUID();
        lenient().when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(inv, "INV-00042", new BigDecimal("1500"), new BigDecimal("225"), new BigDecimal("1725")));
        EventView v = service.raise(TENANT, client.getId(), e.getId(), ME, TODAY);
        assertThat(v.status()).isEqualTo("INVOICED");
        assertThat(v.invoiceNumber()).isEqualTo("INV-00042");
        ArgumentCaptor<InvoicingFacade.DraftInvoice> sent = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(any(), sent.capture());
        assertThat(sent.getValue().customerId()).isEqualTo(CUSTOMER);
        assertThat(sent.getValue().lines()).hasSize(1);
        assertThat(sent.getValue().lines().get(0).unitPrice()).isEqualByComparingTo("1500");
        assertThat(sent.getValue().lines().get(0).vatRate()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("A company that is not VAT registered is billed at zero VAT")
    void notRegistered() {
        lenient().when(invoicing.vatTreatment(any(), any())).thenReturn(new InvoicingFacade.VatTreatment(false, BigDecimal.ZERO, true, LocalDate.of(2020, 1, 1)));
        ComplianceBillingEvent e = event(TODAY);
        lenient().when(invoicing.createDraftInvoice(any(), any())).thenReturn(new InvoicingFacade.DraftInvoiceResult(UUID.randomUUID(), "INV-1", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE));
        service.raise(TENANT, client.getId(), e.getId(), ME, TODAY);
        ArgumentCaptor<InvoicingFacade.DraftInvoice> sent = ArgumentCaptor.forClass(InvoicingFacade.DraftInvoice.class);
        verify(invoicing).createDraftInvoice(any(), sent.capture());
        assertThat(sent.getValue().lines().get(0).vatRate()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("A bill that is not due yet, or whose client has no customer, makes no invoice")
    void refusesRaise() {
        ComplianceBillingEvent early = event(TODAY.plusDays(20));
        assertThatThrownBy(() -> service.raise(TENANT, client.getId(), early.getId(), ME, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("not due yet");
        ComplianceClient unlinked = ComplianceClient.create(TENANT, "No customer", null, null, null, null, ME);
        lenient().when(clients.findByIdForTenant(TENANT, unlinked.getId())).thenReturn(Optional.of(unlinked));
        ComplianceAgreement other = ComplianceAgreement.draft(TENANT, unlinked.getId(), UUID.randomUUID(), "CA-000002", BigDecimal.ONE, BigDecimal.ONE, "ZAR",
                new ComplianceAgreement.Terms("x", LocalDate.of(2027, 3, 1), null, "ONCE", BigDecimal.ONE, LocalDate.of(2027, 3, 1), false, 30, null, null), ME);
        other.activate(ME, "Signed copy returned by the director");
        lenient().when(agreements.findOne(TENANT, other.getId())).thenReturn(Optional.of(other));
        ComplianceBillingEvent e = ComplianceBillingEvent.schedule(TENANT, other, TODAY);
        lenient().when(events.findOne(TENANT, e.getId())).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.raise(TENANT, unlinked.getId(), e.getId(), ME, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("customer");
        verify(invoicing, never()).createDraftInvoice(any(), any());
    }

    @Test
    @DisplayName("Releasing is refused while the invoice is live in Invoicing, and allowed once it is cancelled")
    void releases() {
        ComplianceBillingEvent e = event(TODAY);
        UUID inv = UUID.randomUUID();
        e.invoiced(ME, inv, "INV-00042");
        lenient().when(invoicing.findInvoiceStatus(TENANT, inv)).thenReturn(Optional.of("ISSUED"));
        assertThatThrownBy(() -> service.release(TENANT, client.getId(), e.getId(), "Wrong amount on the draft", TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("issued");
        lenient().when(invoicing.findInvoiceStatus(TENANT, inv)).thenReturn(Optional.of("CANCELLED"));
        EventView v = service.release(TENANT, client.getId(), e.getId(), "Wrong amount on the draft", TODAY);
        assertThat(v.status()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("The worklist filter DUE includes bills overdue to raise, and leaves out upcoming and invoiced ones")
    void worklist() {
        ComplianceBillingEvent late = event(TODAY.minusDays(5));
        ComplianceBillingEvent soon = event(TODAY.plusDays(3));
        ComplianceBillingEvent far = event(TODAY.plusDays(30));
        ComplianceBillingEvent done = event(TODAY.minusDays(40));
        done.invoiced(ME, UUID.randomUUID(), "INV-1");
        lenient().when(events.findAllForTenant(TENANT)).thenReturn(List.of(late, soon, far, done));
        List<EventView> due = service.listAll(TENANT, "due", TODAY);
        assertThat(due).hasSize(2);
        assertThat(service.listAll(TENANT, "UPCOMING", TODAY)).hasSize(1);
        assertThat(service.listAll(TENANT, "INVOICED", TODAY)).hasSize(1);
        assertThat(service.listAll(TENANT, null, TODAY)).hasSize(4);
    }
}
