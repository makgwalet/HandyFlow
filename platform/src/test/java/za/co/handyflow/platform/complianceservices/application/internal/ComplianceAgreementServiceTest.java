package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.AgreementDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ComplianceAgreementServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID ME = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 1);

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceQuoteRepository quotes;
    @Mock ComplianceQuoteLineRepository quoteLines;
    @Mock ComplianceAgreementRepository agreements;
    @Mock ComplianceAgreementLineRepository lines;
    @Mock TenantSequenceService sequences;
    @Mock ComplianceAgreementSettingsRepository settings;

    private ComplianceAgreementService service;
    private ComplianceClient client;
    private ComplianceQuote accepted;

    @BeforeEach
    void setUp() {
        service = new ComplianceAgreementService(clients, quotes, quoteLines, agreements, lines, sequences);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        accepted = acceptedQuote(client.getId());
        lenient().when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        lenient().when(quotes.findOne(TENANT, accepted.getId())).thenReturn(Optional.of(accepted));
        lenient().when(quotes.findForClient(TENANT, client.getId())).thenReturn(List.of(accepted));
        lenient().when(agreements.findLiveForQuote(TENANT, accepted.getId())).thenReturn(Optional.empty());
        lenient().when(agreements.findLiveQuoteIds(TENANT, client.getId())).thenReturn(List.of());
        lenient().when(sequences.nextValue(TENANT, "CS_AGREEMENT")).thenReturn(7L);
        lenient().when(quoteLines.findForQuotes(TENANT, List.of(accepted.getId()))).thenReturn(List.of(quoteLine(accepted.getId())));
        lenient().when(lines.findForAgreements(any(), any())).thenReturn(List.of());
    }

    private static ComplianceQuote acceptedQuote(UUID clientId) {
        ComplianceQuote q = ComplianceQuote.draft(TENANT, clientId, "CQ-000001", 1, "ZAR", ME);
        q.edit("Annual compliance", TODAY.plusDays(30), null, null, TODAY);
        q.applyPricing(QuoteRules.sum(List.of(new QuoteRules.Priced(new BigDecimal("4500"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("675"),
                new BigDecimal("5175"), new BigDecimal("15")))), "CONSULTANT", BigDecimal.ZERO);
        q.submit(ME, 1, TODAY);
        q.markSent(ME, TODAY);
        q.accept(ME, "Accepted by email from the director", TODAY);
        return q;
    }

    private static ComplianceQuoteLine quoteLine(UUID quoteId) {
        ComplianceQuoteLine l = ComplianceQuoteLine.of(TENANT, quoteId, new ComplianceQuoteLine.Input("cs-cipc-ar", "CIPC annual return", 1, "FIXED", null,
                null, null, null, false, BigDecimal.ZERO), 0);
        l.priced(new BigDecimal("4500"), BigDecimal.ZERO, new BigDecimal("4500"), BigDecimal.ZERO, new BigDecimal("675"), new BigDecimal("5175"), null);
        return l;
    }

    @Test
    @DisplayName("Creating from an accepted quote fills the terms from the quote and copies its scope")
    void createsWithDefaults() {
        View v = service.create(TENANT, client.getId(), new CreateRequest(accepted.getId(), null), ME, TODAY);
        assertThat(v.status()).isEqualTo("DRAFT");
        assertThat(v.agreementNumber()).isEqualTo("CA-000007");
        assertThat(v.billingFrequency()).isEqualTo("ONCE");
        assertThat(v.billingAmount()).isEqualByComparingTo("4500");
        assertThat(v.startDate()).isEqualTo(TODAY);
        assertThat(v.title()).isEqualTo("Annual compliance");
        verify(agreements).save(any(ComplianceAgreement.class));
        ArgumentCaptor<List<ComplianceAgreementLine>> copied = ArgumentCaptor.forClass(List.class);
        verify(lines).saveAll(copied.capture());
        assertThat(copied.getValue()).hasSize(1);
        assertThat(copied.getValue().get(0).getServiceCode()).isEqualTo("CS-CIPC-AR");
    }

    @Test
    @DisplayName("A quote that has not been accepted cannot become an agreement")
    void refusesUnaccepted() {
        ComplianceQuote draft = ComplianceQuote.draft(TENANT, client.getId(), "CQ-000002", 1, "ZAR", ME);
        lenient().when(quotes.findOne(TENANT, draft.getId())).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new CreateRequest(draft.getId(), null), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("accepted");
    }

    @Test
    @DisplayName("A quote that already has a live agreement is refused")
    void refusesUsedQuote() {
        ComplianceAgreement held = ComplianceAgreement.draft(TENANT, client.getId(), accepted.getId(), "CA-000001", BigDecimal.ONE, BigDecimal.ONE, "ZAR",
                new ComplianceAgreement.Terms("x", TODAY, null, "ONCE", BigDecimal.ONE, TODAY, false, 30, null, null), ME);
        lenient().when(agreements.findLiveForQuote(TENANT, accepted.getId())).thenReturn(Optional.of(held));
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new CreateRequest(accepted.getId(), null), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("CA-000001");
    }

    @Test
    @DisplayName("A quote that belongs to another client is not found")
    void otherClientsQuote() {
        ComplianceQuote other = acceptedQuote(UUID.randomUUID());
        lenient().when(quotes.findOne(TENANT, other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new CreateRequest(other.getId(), null), ME, TODAY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Bad terms come back as a 400")
    void badTerms() {
        TermsRequest bad = new TermsRequest(null, null, null, "WEEKLY", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new CreateRequest(accepted.getId(), bad), ME, TODAY))
                .isInstanceOfSatisfying(HandyFlowException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Only accepted quotes without a live agreement are on offer")
    void availableQuotes() {
        assertThat(service.availableQuotes(TENANT, client.getId())).hasSize(1);
        lenient().when(agreements.findLiveQuoteIds(TENANT, client.getId())).thenReturn(List.of(accepted.getId()));
        assertThat(service.availableQuotes(TENANT, client.getId())).isEmpty();
    }

    @Test
    @DisplayName("Activating needs a real note and moves the agreement to active")
    void activates() {
        ComplianceAgreement a = ComplianceAgreement.draft(TENANT, client.getId(), accepted.getId(), "CA-000001", BigDecimal.ONE, BigDecimal.ONE, "ZAR",
                new ComplianceAgreement.Terms("x", TODAY, null, "ONCE", BigDecimal.ONE, TODAY, false, 30, null, null), ME);
        lenient().when(agreements.findOne(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.activate(TENANT, client.getId(), a.getId(), "ok", ME, TODAY)).isInstanceOf(HandyFlowException.class);
        View v = service.activate(TENANT, client.getId(), a.getId(), "Signed copy returned by the director", ME, TODAY);
        assertThat(v.status()).isEqualTo("ACTIVE");
        assertThat(v.actions()).containsExactly("suspend", "end");
    }

    @Test
    @DisplayName("With the check on, the person who drafted an agreement cannot activate it; someone else can")
    void secondPersonMustActivate() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        ComplianceAgreementSettings on = ComplianceAgreementSettings.blank(TENANT);
        on.apply(true, null, ME);
        when(settings.findOne(TENANT)).thenReturn(Optional.of(on));
        ComplianceAgreement a = ComplianceAgreement.draft(TENANT, client.getId(), accepted.getId(), "CA-000002", BigDecimal.ONE, BigDecimal.ONE, "ZAR",
                new ComplianceAgreement.Terms("x", TODAY, null, "ONCE", BigDecimal.ONE, TODAY, false, 30, null, null), ME);
        lenient().when(agreements.findOne(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.activate(TENANT, client.getId(), a.getId(), "Signed copy returned by the director", ME, TODAY))
                .isInstanceOfSatisfying(HandyFlowException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(service.activate(TENANT, client.getId(), a.getId(), "Signed copy returned by the director", other, TODAY).status()).isEqualTo("ACTIVE");
    }
}
