package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CompliancePortalDataServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID PORTAL_USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock CompliancePortalGrantRepository grants;
    @Mock ComplianceClientRepository clients;
    @Mock ComplianceQuoteRepository quotes;
    @Mock ComplianceQuoteLineRepository quoteLines;
    @Mock ComplianceAgreementRepository agreements;
    @Mock ComplianceAgreementLineRepository agreementLines;
    @Mock ComplianceBillingEventRepository billing;
    @Mock InvoicingFacade invoicing;

    private CompliancePortalDataService service;
    private ComplianceClient client;

    @BeforeEach
    void setUp() {
        service = new CompliancePortalDataService(grants, clients, quotes, quoteLines, agreements, agreementLines, billing, invoicing);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        lenient().when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        lenient().when(grants.findActiveForUser(PORTAL_USER)).thenReturn(List.of(grant(client.getId())));
    }

    private static CompliancePortalGrant grant(UUID clientId) {
        CompliancePortalGrant g = CompliancePortalGrant.invite(TENANT.getValue(), clientId, "jo@acme.co.za", ME, Instant.now());
        g.accept(PORTAL_USER, Instant.now());
        return g;
    }

    private ComplianceQuote quote(boolean sent) {
        ComplianceQuote q = ComplianceQuote.draft(TENANT, client.getId(), "CQ-000001", 1, "ZAR", ME);
        q.edit("Annual compliance", TODAY.plusDays(30), null, null, TODAY);
        q.applyPricing(QuoteRules.sum(List.of(new QuoteRules.Priced(new BigDecimal("4500"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("675"),
                new BigDecimal("5175"), new BigDecimal("15")))), "CONSULTANT", BigDecimal.ZERO);
        if (sent) {
            q.submit(ME, 1, TODAY);
            q.markSent(ME, TODAY);
        }
        return q;
    }

    @Test
    @DisplayName("A person with no active grant sees nothing and is refused")
    void noGrant() {
        lenient().when(grants.findActiveForUser(PORTAL_USER)).thenReturn(List.of());
        assertThatThrownBy(() -> service.overview(PORTAL_USER, null, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("No active client access");
        assertThat(service.myClients(PORTAL_USER)).isEmpty();
    }

    @Test
    @DisplayName("A client id the person holds no grant for is refused, not trusted")
    void otherClient() {
        assertThatThrownBy(() -> service.quotes(PORTAL_USER, UUID.randomUUID(), TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("do not have access");
    }

    @Test
    @DisplayName("With two clients the person must say which one")
    void severalClients() {
        ComplianceClient other = ComplianceClient.create(TENANT, "Other", null, null, null, null, ME);
        lenient().when(grants.findActiveForUser(PORTAL_USER)).thenReturn(List.of(grant(client.getId()), grant(other.getId())));
        assertThatThrownBy(() -> service.quotes(PORTAL_USER, null, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("several clients");
    }

    @Test
    @DisplayName("Two invites for the same client count as one")
    void duplicateGrants() {
        lenient().when(grants.findActiveForUser(PORTAL_USER)).thenReturn(List.of(grant(client.getId()), grant(client.getId())));
        assertThat(service.myClients(PORTAL_USER)).hasSize(1);
    }

    @Test
    @DisplayName("Quotes still in the practice's hands are not shown; a sent one is, without any internal fields")
    void quoteVisibility() {
        ComplianceQuote draft = quote(false);
        ComplianceQuote sent = quote(true);
        lenient().when(quotes.findForClient(TENANT, client.getId())).thenReturn(List.of(draft, sent));
        lenient().when(quoteLines.findForQuotes(any(), any())).thenReturn(List.of());
        List<Quote> shown = service.quotes(PORTAL_USER, null, TODAY);
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).status()).isEqualTo("SENT");
        assertThat(shown.get(0).total()).isEqualByComparingTo("5175");
    }

    @Test
    @DisplayName("A sent quote past its date is shown as expired")
    void expired() {
        ComplianceQuote sent = quote(true);
        lenient().when(quotes.findForClient(TENANT, client.getId())).thenReturn(List.of(sent));
        lenient().when(quoteLines.findForQuotes(any(), any())).thenReturn(List.of());
        assertThat(service.quotes(PORTAL_USER, null, TODAY.plusDays(60)).get(0).status()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("Only invoices past draft and not cancelled are shown, with what is still owing")
    void invoiceVisibility() {
        ComplianceAgreement a = ComplianceAgreement.draft(TENANT, client.getId(), UUID.randomUUID(), "CA-000001", BigDecimal.ONE, BigDecimal.ONE, "ZAR",
                new ComplianceAgreement.Terms("Annual", TODAY, null, "MONTHLY", new BigDecimal("1500"), TODAY, false, 30, null, null), ME);
        a.activate(ME, "Signed copy returned by the director");
        ComplianceBillingEvent issued = ComplianceBillingEvent.schedule(TENANT, a, TODAY);
        ComplianceBillingEvent draftInv = ComplianceBillingEvent.schedule(TENANT, a, TODAY.plusMonths(1));
        UUID issuedId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        issued.invoiced(ME, issuedId, "INV-00001");
        draftInv.invoiced(ME, draftId, "INV-00002");
        lenient().when(agreements.findForClient(TENANT, client.getId())).thenReturn(List.of(a));
        lenient().when(billing.findForAgreement(TENANT, a.getId())).thenReturn(List.of(issued, draftInv));
        lenient().when(invoicing.findInvoiceSummaries(any(), any())).thenReturn(List.of(
                new InvoicingFacade.InvoiceSummary(issuedId, "INV-00001", "PARTIALLY_PAID", Instant.now(), new BigDecimal("1725"), new BigDecimal("725"), null),
                new InvoicingFacade.InvoiceSummary(draftId, "INV-00002", "DRAFT", null, new BigDecimal("1725"), null, null)));
        List<Invoice> shown = service.invoices(PORTAL_USER, null, TODAY);
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).invoiceNumber()).isEqualTo("INV-00001");
        assertThat(shown.get(0).outstanding()).isEqualByComparingTo("1000");
    }
}
