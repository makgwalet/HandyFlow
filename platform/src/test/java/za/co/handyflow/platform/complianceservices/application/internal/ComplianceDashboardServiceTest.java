package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.WorkQueueDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ComplianceDashboardServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceWorkQueueService queueService;
    @Mock ComplianceClientRepository clients;
    @Mock ClientComplianceDeadlineRepository deadlines;
    @Mock ComplianceQuoteRepository quotes;
    @Mock ComplianceAgreementRepository agreements;
    @Mock ComplianceBillingEventRepository billing;
    @Mock ClientTenderRepository tenders;

    private ComplianceDashboardService service;
    private ComplianceClient active;
    private ComplianceClient inactive;

    @BeforeEach
    void setUp() {
        service = new ComplianceDashboardService(queueService, clients, deadlines, quotes, agreements, billing, tenders);
        active = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        inactive = ComplianceClient.create(TENANT, "Dormant", null, null, null, null, USER);
        inactive.deactivate(USER);
        lenient().when(clients.findAll(eq(TENANT), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(active, inactive)));
        lenient().when(queueService.queue(TENANT, false, TODAY)).thenReturn(new WorkQueueResponse(new Counts(0, 0, 0, 0), List.of()));
        lenient().when(deadlines.findPendingDueBy(eq(TENANT), any())).thenReturn(List.of());
        lenient().when(quotes.findAllForTenant(TENANT)).thenReturn(List.of());
        lenient().when(agreements.findAllForTenant(TENANT)).thenReturn(List.of());
        lenient().when(billing.findAllForTenant(TENANT)).thenReturn(List.of());
        lenient().when(tenders.findAllForTenant(TENANT)).thenReturn(List.of());
    }

    @Test @DisplayName("only active clients count, and deadlines of inactive clients are left out")
    void activeOnly() {
        var mine = ClientComplianceDeadline.create(TENANT, active.getId(), null, "ANNUAL_RETURN", "Annual return", TODAY.minusDays(2), USER);
        var theirs = ClientComplianceDeadline.create(TENANT, inactive.getId(), null, "ANNUAL_RETURN", "Annual return", TODAY.minusDays(2), USER);
        var soon = ClientComplianceDeadline.create(TENANT, active.getId(), null, "VAT", "VAT return", TODAY.plusDays(5), USER);
        lenient().when(deadlines.findPendingDueBy(eq(TENANT), any())).thenReturn(List.of(mine, theirs, soon));
        var s = service.summary(TENANT, false, TODAY);
        assertThat(s.kpis().activeClients()).isEqualTo(1);
        assertThat(s.kpis().overdueDeadlines()).isEqualTo(1);
        assertThat(s.kpis().deadlinesNext14()).isEqualTo(1);
        assertThat(s.upcoming().size()).isEqualTo(2);
        assertThat(s.clientsByStatus().size()).isEqualTo(2);
    }

    @Test @DisplayName("an empty practice gives zeros and empty lists, not errors")
    void empty() {
        var s = service.summary(TENANT, false, TODAY);
        assertThat(s.kpis().quotesOut()).isEqualTo(0);
        assertThat(s.quotePipeline().isEmpty()).isTrue();
        assertThat(s.tenderPipeline().isEmpty()).isTrue();
        assertThat(s.deadlineWeeks().size()).isEqualTo(8);
        assertThat(s.billingForecast().size()).isEqualTo(6);
        assertThat(s.urgent().isEmpty()).isTrue();
    }

    @Test @DisplayName("the queue's most urgent items and type counts come through")
    void fromQueue() {
        var items = List.of(
                new QueueEntry("CASE", "HIGH", active.getId(), "Acme", "Case A", "open", null, "cases"),
                new QueueEntry("CASE", "LOW", active.getId(), "Acme", "Case B", "open", null, "cases"),
                new QueueEntry("DOCUMENT_REVIEW", "LOW", active.getId(), "Acme", "Doc", "waiting", null, "documents"));
        lenient().when(queueService.queue(TENANT, false, TODAY)).thenReturn(new WorkQueueResponse(new Counts(3, 1, 0, 2), items));
        var s = service.summary(TENANT, false, TODAY);
        assertThat(s.kpis().openCases()).isEqualTo(2);
        assertThat(s.kpis().documentsToReview()).isEqualTo(1);
        assertThat(s.urgent().size()).isEqualTo(3);
        assertThat(s.queue().high()).isEqualTo(1);
    }
}
