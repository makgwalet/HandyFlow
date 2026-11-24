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
import za.co.handyflow.platform.complianceservices.dto.ClientObligationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientObligationServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceClientFieldSourceRepository fieldSources;
    @Mock ComplianceObligationRepository obligations;
    @Mock ComplianceObligationVersionRepository versions;
    @Mock ComplianceObligationConditionRepository conditions;
    @Mock ComplianceClientObligationRepository assignments;
    @Mock ComplianceClientObligationHistoryRepository history;

    private ClientObligationService service;
    private ComplianceClient client;
    private ComplianceObligation obligation;
    private ComplianceObligationVersion version;

    @BeforeEach
    void setUp() {
        service = new ClientObligationService(clients, fieldSources, obligations, versions, conditions, assignments, history);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        client.updateProfile(new ComplianceClient.Profile("Acme (Pty) Ltd", null, null, "PTY_LTD", null, null, null, null, null, null, null, null, null), USER);
        when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        when(fieldSources.findForClient(TENANT, client.getId())).thenReturn(List.of());
        obligation = ComplianceObligation.create(TENANT, "CIPC-AR", USER);
        version = ComplianceObligationVersion.draft(TENANT, obligation.getId(), 1, USER);
        version.edit(new ComplianceObligationVersion.Content("Annual return", null, "Act s33", null, null, "ANNUAL", "NONE", null, null, null, null,
                null, "HIGH", "CLIENT", null, null), USER);
        version.submit(USER);
        version.approve(UUID.randomUUID(), LocalDate.of(2025, 1, 1), null, null);
        when(obligations.findAllForTenant(TENANT)).thenReturn(List.of(obligation));
        when(versions.findAllForTenant(TENANT)).thenReturn(List.of(version));
    }

    private void withCondition(String field, String op, String value) {
        ObligationRules.Condition c = ObligationRules.condition(field, op, value);
        ComplianceObligationCondition row = ComplianceObligationCondition.of(TENANT, version.getId(), c, 0);
        when(conditions.findForVersions(any(), any())).thenReturn(List.of(row));
    }

    @Test @DisplayName("refresh adds an assignment for an obligation in force and records why")
    void refreshAdds() {
        withCondition("ENTITY_TYPE", "IN", "PTY_LTD");
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of());
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));
        service.refresh(TENANT, client.getId(), USER, TODAY);
        ArgumentCaptor<ComplianceClientObligation> cap = ArgumentCaptor.forClass(ComplianceClientObligation.class);
        verify(assignments).save(cap.capture());
        assertThat(cap.getValue().getState()).isEqualTo("APPLICABLE");
        assertThat(cap.getValue().isBasedOnUnconfirmed()).isTrue();
        verify(history).save(any());
    }

    @Test @DisplayName("an obligation with no conditions is added as pending, not applicable")
    void refreshNoConditions() {
        when(conditions.findForVersions(any(), any())).thenReturn(List.of());
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of());
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));
        service.refresh(TENANT, client.getId(), USER, TODAY);
        ArgumentCaptor<ComplianceClientObligation> cap = ArgumentCaptor.forClass(ComplianceClientObligation.class);
        verify(assignments).save(cap.capture());
        assertThat(cap.getValue().getState()).isEqualTo("PENDING");
    }

    @Test @DisplayName("an obligation not yet in force is not assigned")
    void notInForce() {
        ComplianceObligationVersion future = ComplianceObligationVersion.draft(TENANT, obligation.getId(), 1, USER);
        future.edit(new ComplianceObligationVersion.Content("AR", null, "Act", null, null, "ANNUAL", "NONE", null, null, null, null, null, "HIGH", "CLIENT", null, null), USER);
        future.submit(USER);
        future.approve(UUID.randomUUID(), TODAY.plusDays(30), null, null);
        when(versions.findAllForTenant(TENANT)).thenReturn(List.of(future));
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of());
        service.refresh(TENANT, client.getId(), USER, TODAY);
        verify(assignments, times(0)).save(any());
    }

    @Test @DisplayName("a person's decision survives a refresh")
    void refreshKeepsDecision() {
        withCondition("ENTITY_TYPE", "IN", "PTY_LTD");
        ComplianceClientObligation a = ComplianceClientObligation.suggested(TENANT, client.getId(), obligation.getId(), version.getId(), 1,
                ApplicabilityRules.evaluate(List.of(), new ApplicabilityRules.Facts(null, null, null, null, null), java.util.Set.of()));
        a.decide("NOT_APPLICABLE", "Client has no employees, confirmed by payroll letter", null, null, TODAY.plusMonths(6), USER, TODAY);
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of(a));
        service.refresh(TENANT, client.getId(), USER, TODAY);
        assertThat(a.getState()).isEqualTo("NOT_APPLICABLE");
        assertThat(a.getSuggestedState()).isEqualTo("APPLICABLE");
    }

    @Test @DisplayName("deciding an obligation that has not been suggested yet is not found")
    void decideUnknown() {
        UUID other = UUID.randomUUID();
        when(assignments.findOne(TENANT, client.getId(), other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.decide(TENANT, client.getId(), other, new DecisionRequest("APPLICABLE", null, null, null, null), USER, TODAY))
                .isInstanceOf(RuntimeException.class);
    }

    @Test @DisplayName("an override without a reason becomes a 400")
    void overrideNeedsReason() {
        ComplianceClientObligation a = ComplianceClientObligation.suggested(TENANT, client.getId(), obligation.getId(), version.getId(), 1,
                ApplicabilityRules.evaluate(List.of(), new ApplicabilityRules.Facts(null, null, null, null, null), java.util.Set.of()));
        when(assignments.findOne(TENANT, client.getId(), obligation.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.decide(TENANT, client.getId(), obligation.getId(), new DecisionRequest("APPLICABLE", "no", null, null, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("reason");
    }
}
