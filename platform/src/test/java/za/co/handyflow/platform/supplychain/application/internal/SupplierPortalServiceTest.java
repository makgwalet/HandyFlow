package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.SupplierPortalService.Access;
import za.co.handyflow.platform.supplychain.domain.enums.QuotationStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScQuotation;
import za.co.handyflow.platform.supplychain.domain.model.ScRfq;
import za.co.handyflow.platform.supplychain.domain.model.ScRfqInvitation;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.QuotationRequest;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.QuoteRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The portal's safety rules: the supplier comes from the login, never the request; only invited RFQs are reachable; writes reuse QuotationService. */
class SupplierPortalServiceTest {

    private final ScSupplierPortalGrantRepository grants = mock(ScSupplierPortalGrantRepository.class);
    private final PortalUserRepository portalUsers = mock(PortalUserRepository.class);
    private final QuotationService quotations = mock(QuotationService.class);
    private final ScRfqRepository rfqs = mock(ScRfqRepository.class);
    private final ScRfqLineRepository rfqLines = mock(ScRfqLineRepository.class);
    private final ScRfqInvitationRepository invites = mock(ScRfqInvitationRepository.class);
    private final ScQuotationRepository quotes = mock(ScQuotationRepository.class);
    private final ScRfqEventRepository events = mock(ScRfqEventRepository.class);
    private final FeatureGuard featureGuard = mock(FeatureGuard.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private SupplierPortalService service;
    private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID(), supplier = UUID.randomUUID(), rfqId = UUID.randomUUID();
    private final Access access = new Access(tenant, supplier, "Acme Parts", "Buyer Co");

    @BeforeEach
    void setUp() {
        service = spy(new SupplierPortalService(grants, portalUsers, quotations, rfqs, rfqLines, invites, quotes, events, featureGuard, jdbc));
        doReturn(List.of(access)).when(service).accessFor(user);
        PortalUser pu = mock(PortalUser.class);
        when(pu.getFullName()).thenReturn("Sam Supplier");
        when(portalUsers.findById(user)).thenReturn(Optional.of(pu));
    }

    private ScRfqInvitation invited() {
        ScRfqInvitation i = ScRfqInvitation.create(tenant, rfqId, supplier, "Acme Parts", "sam@acme.test");
        when(invites.findByRfqAndSupplier(tenant, rfqId, supplier)).thenReturn(Optional.of(i));
        return i;
    }

    private void rfqOpen(boolean open) {
        ScRfq rfq = mock(ScRfq.class);
        when(rfq.isOpenForQuotes(any(Instant.class))).thenReturn(open);
        when(rfqs.findByTenantAndId(tenant, rfqId)).thenReturn(Optional.of(rfq));
    }

    @Test
    void theSupplierOnASubmittedQuotationIsTheOneOnTheLoginAndTheActorIsNamed() {
        invited();
        QuoteRequest body = new QuoteRequest("Q-1", null, 14, 12, 30, BigDecimal.ZERO, BigDecimal.ZERO, "n",
                List.of(new QuotationRequest.Line(UUID.randomUUID(), false, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, null)));

        service.submit(user, rfqId, body);

        ArgumentCaptor<QuotationRequest> sent = ArgumentCaptor.forClass(QuotationRequest.class);
        ArgumentCaptor<String> who = ArgumentCaptor.forClass(String.class);
        verify(quotations).submit(eq(TenantId.of(tenant)), eq(rfqId), eq(user), who.capture(), sent.capture());
        assertThat(sent.getValue().supplierId()).isEqualTo(supplier);
        assertThat(sent.getValue().quoteReference()).isEqualTo("Q-1");
        assertThat(who.getValue()).contains("Sam Supplier").contains("Acme Parts").contains("supplier portal");
    }

    @Test
    void anRfqTheSupplierWasNotInvitedToIsNotFoundAndNothingIsWritten() {
        when(invites.findByRfqAndSupplier(any(), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(user, rfqId, new QuoteRequest(null, null, 1, null, null, null, null, null, List.of())))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("RFQ not found");
        verifyNoInteractions(quotations);
    }

    @Test
    void aPersonWithNoActiveSupplierAccessSeesNothing() {
        doReturn(List.of()).when(service).accessFor(user);
        assertThat(service.rfqs(user)).isEmpty();
        assertThatThrownBy(() -> service.rfq(user, rfqId)).isInstanceOf(HandyFlowException.class);
        verifyNoInteractions(quotations, rfqs);
    }

    @Test
    void representingTwoInvitedSuppliersOnOneRfqIsRefusedRatherThanGuessed() {
        UUID other = UUID.randomUUID();
        doReturn(List.of(access, new Access(tenant, other, "Other Co", "Buyer Co"))).when(service).accessFor(user);
        invited();
        when(invites.findByRfqAndSupplier(tenant, rfqId, other)).thenReturn(Optional.of(ScRfqInvitation.create(tenant, rfqId, other, "Other Co", null)));
        assertThatThrownBy(() -> service.submit(user, rfqId, new QuoteRequest(null, null, 1, null, null, null, null, null, List.of())))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("more than one supplier");
        verifyNoInteractions(quotations);
    }

    @Test
    void decliningNeedsAReasonAnOpenRfqAndNoLiveQuotation() {
        invited();
        rfqOpen(false);
        assertThatThrownBy(() -> service.decline(user, rfqId, "too busy")).hasMessageContaining("no longer open");

        rfqOpen(true);
        assertThatThrownBy(() -> service.decline(user, rfqId, " ")).hasMessageContaining("why you are declining");

        ScQuotation live = mock(ScQuotation.class);
        when(live.getStatus()).thenReturn(QuotationStatus.SUBMITTED);
        when(quotes.findByRfqAndSupplier(tenant, rfqId, supplier)).thenReturn(Optional.of(live));
        assertThatThrownBy(() -> service.decline(user, rfqId, "too busy")).hasMessageContaining("withdraw it first");
        verify(events, never()).save(any());
    }

    @Test
    void withdrawingOnAClosedRfqIsRefused() {
        invited();
        rfqOpen(false);
        assertThatThrownBy(() -> service.withdraw(user, rfqId, "price error")).hasMessageContaining("no longer open");
        verify(quotations, never()).withdraw(any(), any(), any(), anyString(), any(), anyString());
    }
}
