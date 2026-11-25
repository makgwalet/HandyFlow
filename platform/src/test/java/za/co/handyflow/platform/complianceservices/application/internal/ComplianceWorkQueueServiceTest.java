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
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceWorkQueueServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Mock ComplianceClientRepository clients;
    @Mock ClientComplianceDeadlineRepository deadlines;
    @Mock ClientComplianceDocumentRepository documents;
    @Mock ComplianceClientObligationRepository assignments;
    @Mock ComplianceObligationRepository obligations;
    @Mock ComplianceObligationVersionRepository versions;
    @Mock ComplianceAssessmentRepository assessments;
    @Mock ComplianceCaseRepository cases;
    @Mock ComplianceRegChangeRepository regChanges;
    @Mock ComplianceRegImpactRepository regImpacts;
    @Mock ComplianceReviewRepository qualityReviews;
    @Mock CompliancePortalQueryRepository portalQueries;
    @Mock ComplianceDocumentRequestRepository documentRequests;
    @Mock ComplianceProviderRepository providerRepo;
    @Mock ComplianceProviderAccreditationRepository providerAccreditations;
    @Mock ComplianceProviderAssignmentRepository providerAssignments;
    @Mock ComplianceProviderDeliverableRepository providerDeliverables;

    private ComplianceWorkQueueService service;
    private ComplianceClient active;
    private ComplianceClient other;

    @BeforeEach
    void setUp() {
        service = new ComplianceWorkQueueService(clients, deadlines, documents, assignments, obligations, versions, assessments, cases, regChanges, regImpacts, qualityReviews, portalQueries, documentRequests, providerRepo, providerAccreditations, providerAssignments, providerDeliverables);
        active = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        other = ComplianceClient.create(TENANT, "Other", null, null, null, null, USER);
        when(clients.findAll(eq(TENANT), eq("ACTIVE"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(active)));
        when(obligations.findAllForTenant(TENANT)).thenReturn(List.of());
        when(deadlines.findPendingDueBy(eq(TENANT), any())).thenReturn(List.of());
        when(documents.findNeedingAttention(eq(TENANT), any())).thenReturn(List.of());
        when(assignments.findAwaitingApproval(TENANT)).thenReturn(List.of());
        when(versions.findInReview(TENANT)).thenReturn(List.of());
        when(assessments.findDraftsForTenant(TENANT)).thenReturn(List.of());
        when(cases.findLiveForTenant(TENANT)).thenReturn(List.of());
        when(regChanges.findAwaitingReview(TENANT)).thenReturn(List.of());
        when(regImpacts.findLive(TENANT)).thenReturn(List.of());
        when(qualityReviews.findNeedingAttention(TENANT)).thenReturn(List.of());
        when(qualityReviews.findPreviousIds(TENANT)).thenReturn(List.of());
        when(portalQueries.findAwaitingAnswer(TENANT)).thenReturn(List.of());
        when(documentRequests.findOpen(TENANT)).thenReturn(List.of());
        when(providerRepo.findAllForTenant(TENANT)).thenReturn(List.of());
        when(providerAccreditations.findLiveForActiveProviders(TENANT)).thenReturn(List.of());
        when(providerAssignments.findOpen(TENANT)).thenReturn(List.of());
        when(providerDeliverables.findAwaitingReview(TENANT)).thenReturn(List.of());
    }

    @Test @DisplayName("an overdue deadline of an active client is high, and a deadline of an inactive client is left out")
    void deadlines() {
        var mine = ClientComplianceDeadline.create(TENANT, active.getId(), null, "ANNUAL_RETURN", "Annual return", TODAY.minusDays(2), USER);
        var theirs = ClientComplianceDeadline.create(TENANT, other.getId(), null, "ANNUAL_RETURN", "Annual return", TODAY.minusDays(2), USER);
        when(deadlines.findPendingDueBy(eq(TENANT), any())).thenReturn(List.of(mine, theirs));
        var q = service.queue(TENANT, false, TODAY);
        assertThat(q.items()).hasSize(1);
        assertThat(q.items().get(0).clientName()).isEqualTo("Acme");
        assertThat(q.counts().high()).isEqualTo(1);
    }

    @Test @DisplayName("a verified document that has expired is high and one waiting for review is not")
    void documentsSplit() {
        var expired = ClientComplianceDocument.create(UUID.randomUUID(), TENANT, active.getId(), null, "BBBEE Certificate", UUID.randomUUID(), null, TODAY.minusDays(3), USER);
        expired.verify(UUID.randomUUID(), null);
        var waiting = ClientComplianceDocument.create(UUID.randomUUID(), TENANT, active.getId(), null, "Tax Clearance", UUID.randomUUID(), null, null, USER);
        when(documents.findNeedingAttention(eq(TENANT), any())).thenReturn(List.of(waiting, expired));
        var q = service.queue(TENANT, false, TODAY);
        assertThat(q.items()).hasSize(2);
        assertThat(q.items().get(0).type()).isEqualTo("DOCUMENT_EXPIRY");
        assertThat(q.items().get(1).type()).isEqualTo("DOCUMENT_REVIEW");
    }

    @Test @DisplayName("catalogue reviews and approvals only appear for an admin")
    void adminOnlyItems() {
        var o = ComplianceObligation.create(TENANT, "POPIA-1", USER);
        var v = ComplianceObligationVersion.draft(TENANT, o.getId(), 1, USER);
        when(obligations.findAllForTenant(TENANT)).thenReturn(List.of(o));
        when(versions.findInReview(TENANT)).thenReturn(List.of(v));
        assertThat(service.queue(TENANT, false, TODAY).items()).isEmpty();
        var admin = service.queue(TENANT, true, TODAY);
        assertThat(admin.items()).hasSize(1);
        assertThat(admin.items().get(0).type()).isEqualTo("CATALOGUE_REVIEW");
        assertThat(admin.items().get(0).clientName()).isNull();
    }

    @Test @DisplayName("an empty queue has zero counts")
    void empty() {
        var q = service.queue(TENANT, true, TODAY);
        assertThat(q.items()).isEmpty();
        assertThat(q.counts().total()).isEqualTo(0);
    }

    @Test @DisplayName("a change waiting for review is for admins only, and a live impact of an active client shows with the change's date")
    void regulatory() {
        var source = UUID.randomUUID();
        var change = ComplianceRegChange.raise(TENANT, source, "Annual return fee change", null, "AMENDED", null, TODAY.plusDays(5), null, USER, TODAY);
        when(regChanges.findAwaitingReview(TENANT)).thenReturn(List.of(change));
        var impact = ComplianceRegImpact.open(TENANT, change.getId(), active.getId(), UUID.randomUUID(), TODAY.plusDays(5));
        var stranger = ComplianceRegImpact.open(TENANT, change.getId(), other.getId(), UUID.randomUUID(), TODAY.plusDays(5));
        when(regImpacts.findLive(TENANT)).thenReturn(List.of(impact, stranger));
        when(regChanges.findByIds(eq(TENANT), any())).thenReturn(List.of(change));

        var staff = service.queue(TENANT, false, TODAY);
        assertThat(staff.items().stream().filter(i -> i.type().equals("REGULATORY_REVIEW")).count()).isEqualTo(0L);
        assertThat(staff.items().stream().filter(i -> i.type().equals("REGULATORY_IMPACT")).count()).isEqualTo(1L);
        assertThat(staff.items().get(0).severity()).isEqualTo("HIGH");

        var admin = service.queue(TENANT, true, TODAY);
        assertThat(admin.items().stream().filter(i -> i.type().equals("REGULATORY_REVIEW")).count()).isEqualTo(1L);
    }
}
