package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.RegulatoryDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceRegulatoryServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID RAISER = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceRegSourceRepository sources;
    @Mock ComplianceRegChangeRepository changes;
    @Mock ComplianceRegChangeObligationRepository links;
    @Mock ComplianceRegImpactRepository impacts;
    @Mock ComplianceObligationRepository obligations;
    @Mock ComplianceObligationVersionRepository versions;
    @Mock ComplianceClientObligationRepository assignments;
    @Mock ComplianceClientRepository clients;

    private ComplianceRegulatoryService service;
    private ComplianceRegSource source;
    private ComplianceClient acme;
    private ComplianceClient gone;
    private ComplianceObligation obligation;

    @BeforeEach
    void setUp() {
        service = new ComplianceRegulatoryService(sources, changes, links, impacts, obligations, versions, assignments, clients);
        source = ComplianceRegSource.create(TENANT, "Government Gazette", null, null, "NOTICE", RAISER);
        acme = ComplianceClient.create(TENANT, "Acme", null, null, null, null, RAISER);
        gone = ComplianceClient.create(TENANT, "Gone Ltd", null, null, null, null, RAISER);
        obligation = ComplianceObligation.create(TENANT, "CIPC-AR", RAISER);
        lenient().when(sources.findOne(TENANT, source.getId())).thenReturn(Optional.of(source));
        lenient().when(sources.findAllForTenant(TENANT)).thenReturn(List.of(source));
        lenient().when(obligations.findAllForTenant(TENANT)).thenReturn(List.of(obligation));
        lenient().when(obligations.findByIdForTenant(TENANT, obligation.getId())).thenReturn(Optional.of(obligation));
        lenient().when(versions.findAllForTenant(TENANT)).thenReturn(List.of());
        lenient().when(clients.findAll(eq(TENANT), eq("ACTIVE"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(acme)));
        lenient().when(clients.findAll(eq(TENANT), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(acme, gone)));
        lenient().when(changes.save(any(ComplianceRegChange.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(impacts.findAllForTenant(TENANT)).thenReturn(List.of());
        lenient().when(links.findAllForTenant(TENANT)).thenReturn(List.of());
    }

    private ComplianceClientObligation assigned(ComplianceClient c, String state) {
        var a = ComplianceClientObligation.suggested(TENANT, c.getId(), obligation.getId(), UUID.randomUUID(), 1,
                new ApplicabilityRules.Evaluation(state, List.of(new ApplicabilityRules.Check("-", ApplicabilityRules.Outcome.MET, "reason", true)), false));
        return a;
    }

    private ComplianceRegChange inReview() {
        var c = ComplianceRegChange.raise(TENANT, source.getId(), "Fee change", null, "AMENDED", null, TODAY.plusDays(30), null, RAISER, TODAY);
        c.startReview(REVIEWER);
        when(changes.findOne(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(links.findForChange(TENANT, c.getId())).thenReturn(List.of(ComplianceRegChangeObligation.link(TENANT, c.getId(), obligation.getId())));
        return c;
    }

    @Test @DisplayName("confirming raises an impact for each active client the obligation applies to, and none for inactive or not-applicable ones")
    void confirmRaisesImpacts() {
        var c = inReview();
        when(assignments.findForObligations(eq(TENANT), any(Collection.class))).thenReturn(List.of(assigned(acme, "APPLICABLE"), assigned(gone, "APPLICABLE")));
        when(impacts.findForChange(TENANT, c.getId())).thenReturn(List.of());

        service.confirm(TENANT, c.getId(), new ReviewRequest("Checked against the gazette", null), REVIEWER);

        ArgumentCaptor<ComplianceRegImpact> saved = ArgumentCaptor.forClass(ComplianceRegImpact.class);
        verify(impacts, times(1)).save(saved.capture());
        assertThat(saved.getValue().getClientId()).isEqualTo(acme.getId());
        assertThat(saved.getValue().getDueDate()).isEqualTo(TODAY.plusDays(30));
        assertThat(c.getStatus()).isEqualTo("CONFIRMED");
        assertThat(c.getImpactsRaisedAt() != null).isTrue();
    }

    @Test @DisplayName("a client for whom the obligation does not apply gets no impact")
    void notApplicableSkipped() {
        var c = inReview();
        when(assignments.findForObligations(eq(TENANT), any(Collection.class))).thenReturn(List.of(assigned(acme, "NOT_APPLICABLE")));
        when(impacts.findForChange(TENANT, c.getId())).thenReturn(List.of());
        service.confirm(TENANT, c.getId(), new ReviewRequest("Checked against the gazette", null), REVIEWER);
        verify(impacts, never()).save(any(ComplianceRegImpact.class));
    }

    @Test @DisplayName("the person who raised the change cannot confirm it without a reason, and nothing is raised")
    void sameReviewerRefused() {
        var c = inReview();
        assertThatThrownBy(() -> service.confirm(TENANT, c.getId(), new ReviewRequest("Checked against the gazette", null), RAISER)).isInstanceOf(HandyFlowException.class);
        verify(impacts, never()).save(any(ComplianceRegImpact.class));
        assertThat(c.getStatus()).isEqualTo("IN_REVIEW");
    }

    @Test @DisplayName("refreshing adds only clients with no impact yet")
    void refreshIsIdempotent() {
        var c = inReview();
        c.confirm(REVIEWER, "Checked against the gazette", null);
        var existing = ComplianceRegImpact.open(TENANT, c.getId(), acme.getId(), obligation.getId(), c.getEffectiveDate());
        when(assignments.findForObligations(eq(TENANT), any(Collection.class))).thenReturn(List.of(assigned(acme, "APPLICABLE")));
        when(impacts.findForChange(TENANT, c.getId())).thenReturn(List.of(existing));
        var result = service.refreshImpacts(TENANT, c.getId());
        assertThat(result.added()).isEqualTo(0);
        verify(impacts, never()).save(any(ComplianceRegImpact.class));
    }

    @Test @DisplayName("a change cannot be raised against a source that is switched off")
    void inactiveSource() {
        source.setActive(false);
        var request = new ChangeRequest(source.getId(), "Fee change", null, "AMENDED", null, null, null, List.of());
        assertThatThrownBy(() -> service.raise(TENANT, request, RAISER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(changes, never()).save(any(ComplianceRegChange.class));
    }

    @Test @DisplayName("an obligation that is not in the catalogue cannot be linked")
    void unknownObligation() {
        var request = new ChangeRequest(source.getId(), "Fee change", null, "AMENDED", null, null, null, List.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.raise(TENANT, request, RAISER, TODAY)).isInstanceOf(RuntimeException.class);
        verify(changes, never()).save(any(ComplianceRegChange.class));
    }
}
