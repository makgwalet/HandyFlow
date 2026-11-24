package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.CalculateRequest;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.CalculateResponse;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.PartView;
import za.co.handyflow.platform.complianceservices.dto.QuoteDtos.*;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceQuoteServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 1);

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceQuoteRepository quotes;
    @Mock ComplianceQuoteLineRepository lines;
    @Mock ComplianceQuoteAdjustmentRepository adjustments;
    @Mock ComplianceServiceDefinitionRepository services;
    @Mock ComplianceServiceVersionRepository serviceVersions;
    @Mock PricingCalculatorService calculator;
    @Mock TenantSequenceService sequences;

    private ComplianceQuoteService service;
    private ComplianceClient client;
    private CompliancePriceBookVersion book;
    private ComplianceServiceDefinition definition;

    @BeforeEach
    void setUp() {
        service = new ComplianceQuoteService(clients, quotes, lines, adjustments, services, serviceVersions, calculator, sequences);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        book = CompliancePriceBookVersion.draft(TENANT, 1, ME);
        book.edit("2027", null);
        book.submit(ME, 1, 0);
        book.approve(REVIEWER, LocalDate.of(2027, 1, 1), null, null);
        definition = ComplianceServiceDefinition.create(TENANT, "CS-CIPC-AR", ME);
        ComplianceServiceVersion sv = ComplianceServiceVersion.draft(TENANT, definition.getId(), 1, ME);
        sv.edit(new ComplianceServiceVersion.Content("CIPC annual return", "cipc", null, null, null, BigDecimal.ONE, null, "consultant", true, "fixed", "on_delivery", null));
        sv.submit(ME, 1);
        sv.approve(REVIEWER, LocalDate.of(2027, 1, 1), null, null);
        lenient().when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        lenient().when(calculator.bookInForce(TENANT, TODAY)).thenReturn(book);
        lenient().when(calculator.bookNumbered(TENANT, 1)).thenReturn(book);
        lenient().when(services.findByCode(TENANT, "CS-CIPC-AR")).thenReturn(Optional.of(definition));
        lenient().when(serviceVersions.findForService(TENANT, definition.getId())).thenReturn(List.of(sv));
        lenient().when(sequences.nextValue(TENANT, "CS_QUOTE")).thenReturn(7L);
        lenient().when(quotes.save(any(ComplianceQuote.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(lines.save(any(ComplianceQuoteLine.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(lines.findForQuotes(any(), any())).thenReturn(List.of());
    }

    private static LineInput line(String code, String discount) {
        return new LineInput(code, null, null, null, null, false, discount == null ? null : new BigDecimal(discount), null, null);
    }

    private void priced(String discountApprover, String professional, String discount, String vat, String total) {
        when(calculator.priceOn(any(), any(), any(CalculateRequest.class))).thenReturn(new CalculateResponse("CS-CIPC-AR", "FIXED", 1, "ZAR",
                List.of(new PartView("Fee", new BigDecimal("2000.00"))), new BigDecimal("5000"), new BigDecimal(discount), new BigDecimal(professional), BigDecimal.ZERO,
                new BigDecimal(vat), new BigDecimal("15"), new BigDecimal(total), discountApprover));
    }

    private ComplianceQuote existing(String status) {
        ComplianceQuote q = ComplianceQuote.draft(TENANT, client.getId(), "CQ-000001", 1, "ZAR", ME);
        q.edit("t", TODAY.plusDays(30), null, null, TODAY);
        if (!"DRAFT".equals(status)) {
            q.applyPricing(QuoteRules.sum(List.of()), "COMPLIANCE_MANAGER", BigDecimal.TEN);
            q.submit(ME, 1, TODAY);
        }
        lenient().when(quotes.findOne(TENANT, q.getId())).thenReturn(Optional.of(q));
        return q;
    }

    @Test @DisplayName("a new quote is numbered and pinned to the price book in force today")
    void create() {
        QuoteView v = service.create(TENANT, client.getId(), new SaveRequest("Annual", null, null, null, List.of()), ME, TODAY);
        assertThat(v.quoteNumber()).isEqualTo("CQ-000007");
        assertThat(v.priceBookVersion()).isEqualTo(1);
        assertThat(v.validUntil()).isEqualTo(TODAY.plusDays(30));
        assertThat(v.status()).isEqualTo("DRAFT");
    }

    @Test @DisplayName("there is no quote without an approved price book in force")
    void noBook() {
        when(calculator.bookInForce(TENANT, TODAY)).thenThrow(new HandyFlowException("No approved price book is in force", HttpStatus.CONFLICT, "PRICE_BOOK_NOT_IN_FORCE"));
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new SaveRequest("x", null, null, null, List.of()), ME, TODAY)).hasMessageContaining("No approved price book");
    }

    @Test @DisplayName("lines are priced by the server, and the deepest discount decides the approver")
    void pricesLines() {
        priced("PRACTICE_OWNER", "4400.00", "600.00", "660.00", "5060.00");
        QuoteView v = service.create(TENANT, client.getId(), new SaveRequest("Annual", null, null, null, List.of(line("cs-cipc-ar", "12"))), ME, TODAY);
        assertThat(v.total()).isEqualByComparingTo("5060.00");
        assertThat(v.discountTotal()).isEqualByComparingTo("600.00");
        assertThat(v.requiredApprover()).isEqualTo("PRACTICE_OWNER");
        assertThat(v.maxDiscountPct()).isEqualByComparingTo("12");
        verify(lines).save(any(ComplianceQuoteLine.class));
    }

    @Test @DisplayName("a service with no approved version in force cannot be quoted")
    void notOnSale() {
        ComplianceServiceDefinition other = ComplianceServiceDefinition.create(TENANT, "CS-NEW", ME);
        when(services.findByCode(TENANT, "CS-NEW")).thenReturn(Optional.of(other));
        when(serviceVersions.findForService(TENANT, other.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new SaveRequest("x", null, null, null, List.of(line("CS-NEW", null))), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not on sale");
        when(services.findByCode(TENANT, "CS-GONE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new SaveRequest("x", null, null, null, List.of(line("CS-GONE", null))), ME, TODAY))
                .hasMessageContaining("not in the service catalogue");
    }

    @Test @DisplayName("a service cannot be on one quote twice")
    void duplicates() {
        priced("CONSULTANT", "5000.00", "0.00", "750.00", "5750.00");
        assertThatThrownBy(() -> service.create(TENANT, client.getId(), new SaveRequest("x", null, null, null, List.of(line("CS-CIPC-AR", null), line("CS-CIPC-AR", null))), ME, TODAY))
                .hasMessageContaining("twice");
    }

    @Test @DisplayName("only a draft can be saved")
    void lockedAfterSubmit() {
        ComplianceQuote q = existing("PENDING");
        assertThatThrownBy(() -> service.save(TENANT, client.getId(), q.getId(), new SaveRequest("x", null, null, null, List.of()), ME, TODAY))
                .isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test @DisplayName("submitting needs a line; a quote with a big discount then waits for an administrator")
    void submitAndApprove() {
        ComplianceQuote q = existing("DRAFT");
        q.applyPricing(QuoteRules.sum(List.of()), "COMPLIANCE_MANAGER", BigDecimal.TEN);
        assertThatThrownBy(() -> service.submit(TENANT, client.getId(), q.getId(), ME, TODAY)).hasMessageContaining("at least one service");
        when(lines.findForQuotes(TENANT, List.of(q.getId()))).thenReturn(List.of(org.mockito.Mockito.mock(ComplianceQuoteLine.class)));
        assertThat(service.submit(TENANT, client.getId(), q.getId(), ME, TODAY).status()).isEqualTo("PENDING_APPROVAL");
        assertThatThrownBy(() -> service.approve(TENANT, client.getId(), q.getId(), new ApproveRequest(null, null), ME, TODAY)).hasMessageContaining("someone else");
        assertThat(service.approve(TENANT, client.getId(), q.getId(), new ApproveRequest("fine", null), REVIEWER, TODAY).status()).isEqualTo("APPROVED");
    }

    @Test @DisplayName("sending, accepting and the client's answer are recorded in order")
    void answerFlow() {
        ComplianceQuote q = existing("PENDING");
        service.approve(TENANT, client.getId(), q.getId(), new ApproveRequest(null, null), REVIEWER, TODAY);
        assertThat(service.markSent(TENANT, client.getId(), q.getId(), ME, TODAY).status()).isEqualTo("SENT");
        assertThatThrownBy(() -> service.accept(TENANT, client.getId(), q.getId(), "ok", ME, TODAY)).hasMessageContaining("at least 10");
        assertThat(service.accept(TENANT, client.getId(), q.getId(), "Accepted by email on 3 March", ME, TODAY).status()).isEqualTo("ACCEPTED");
    }

    @Test @DisplayName("a sent quote past its date shows as expired and cannot be accepted")
    void expired() {
        ComplianceQuote q = existing("PENDING");
        service.approve(TENANT, client.getId(), q.getId(), new ApproveRequest(null, null), REVIEWER, TODAY);
        service.markSent(TENANT, client.getId(), q.getId(), ME, TODAY);
        LocalDate late = TODAY.plusDays(40);
        assertThat(service.get(TENANT, client.getId(), q.getId(), late).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> service.accept(TENANT, client.getId(), q.getId(), "Accepted by email late", ME, late)).hasMessageContaining("expired");
    }

    @Test @DisplayName("a quote is found only under its own client")
    void wrongClient() {
        ComplianceQuote q = existing("DRAFT");
        ComplianceClient other = ComplianceClient.create(TENANT, "Other", null, null, null, null, ME);
        when(clients.findByIdForTenant(TENANT, other.getId())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.get(TENANT, other.getId(), q.getId(), TODAY)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("the all-clients list can be filtered by status, including expired")
    void listAll() {
        ComplianceQuote draft = existing("DRAFT");
        ComplianceQuote sent = existing("PENDING");
        sent.approve(REVIEWER, null, null);
        sent.markSent(ME, TODAY);
        when(quotes.findAllForTenant(TENANT)).thenReturn(List.of(draft, sent));
        assertThat(service.listAll(TENANT, null, TODAY)).hasSize(2);
        assertThat(service.listAll(TENANT, "draft", TODAY)).hasSize(1);
        assertThat(service.listAll(TENANT, "EXPIRED", TODAY.plusDays(40))).hasSize(1);
        assertThat(service.listAll(TENANT, "EXPIRED", TODAY)).isEmpty();
    }
}
