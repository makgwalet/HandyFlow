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
import za.co.handyflow.platform.complianceservices.dto.CaseDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceCaseServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceAssessmentRepository assessments;
    @Mock ComplianceAssessmentItemRepository assessmentItems;
    @Mock ComplianceCaseRepository cases;
    @Mock ComplianceCaseStepRepository steps;
    @Mock ComplianceCaseEventRepository events;

    private ComplianceCaseService service;
    private ComplianceClient client;

    @BeforeEach
    void setUp() {
        service = new ComplianceCaseService(clients, assessments, assessmentItems, cases, steps, events);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        lenient().when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        lenient().when(steps.findForCases(any(), any())).thenReturn(List.of());
        lenient().when(events.findForCase(any(), any())).thenReturn(List.of());
    }

    private ComplianceCase stored(String status) {
        ComplianceCase c = ComplianceCase.open(TENANT, client.getId(), "Fix the officer", null, null, ME, null, null, null, null, ME, TODAY);
        if (!status.equals("OPEN")) c.start();
        lenient().when(cases.findOne(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    @Test @DisplayName("a case taken from a problem item takes its title, severity and code from the item")
    void fromItem() {
        UUID assessmentId = UUID.randomUUID();
        UUID obligationId = UUID.randomUUID();
        var item = ComplianceAssessmentItem.of(TENANT, assessmentId, obligationId, UUID.randomUUID(), 1, "POPIA-1", "Information officer", "HIGH", "APPLICABLE", "MISSING");
        when(assessments.findOne(TENANT, client.getId(), assessmentId)).thenReturn(Optional.of(org.mockito.Mockito.mock(ComplianceAssessment.class)));
        when(assessmentItems.findOne(TENANT, assessmentId, obligationId)).thenReturn(Optional.of(item));
        when(cases.findLive(TENANT, client.getId(), obligationId)).thenReturn(Optional.empty());

        CaseDetail out = service.open(TENANT, client.getId(), new OpenRequest(null, null, null, null, null, assessmentId, obligationId), ME, TODAY);

        assertThat(out.summary().title()).isEqualTo("POPIA-1: Information officer");
        assertThat(out.summary().severity()).isEqualTo("HIGH");
        assertThat(out.summary().obligationCode()).isEqualTo("POPIA-1");
        assertThat(out.summary().ownerId()).isEqualTo(ME);
        verify(events).save(any(ComplianceCaseEvent.class));
    }

    @Test @DisplayName("an item that is not a problem cannot raise a case")
    void notAProblem() {
        UUID assessmentId = UUID.randomUUID();
        UUID obligationId = UUID.randomUUID();
        var item = ComplianceAssessmentItem.of(TENANT, assessmentId, obligationId, UUID.randomUUID(), 1, "X", "Fine", "LOW", "APPLICABLE", "MET");
        when(assessments.findOne(TENANT, client.getId(), assessmentId)).thenReturn(Optional.of(org.mockito.Mockito.mock(ComplianceAssessment.class)));
        when(assessmentItems.findOne(TENANT, assessmentId, obligationId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.open(TENANT, client.getId(), new OpenRequest(null, null, null, null, null, assessmentId, obligationId), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not a problem");
        verify(cases, never()).save(any());
    }

    @Test @DisplayName("a second live case for the same obligation is refused")
    void duplicate() {
        UUID assessmentId = UUID.randomUUID();
        UUID obligationId = UUID.randomUUID();
        var item = ComplianceAssessmentItem.of(TENANT, assessmentId, obligationId, UUID.randomUUID(), 1, "POPIA-1", "Officer", "HIGH", "APPLICABLE", "MISSING");
        when(assessments.findOne(TENANT, client.getId(), assessmentId)).thenReturn(Optional.of(org.mockito.Mockito.mock(ComplianceAssessment.class)));
        when(assessmentItems.findOne(TENANT, assessmentId, obligationId)).thenReturn(Optional.of(item));
        when(cases.findLive(TENANT, client.getId(), obligationId)).thenReturn(Optional.of(stored("OPEN")));

        assertThatThrownBy(() -> service.open(TENANT, client.getId(), new OpenRequest(null, null, null, null, null, assessmentId, obligationId), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already a case");
    }

    @Test @DisplayName("an obligation without an assessment is refused")
    void obligationNeedsAssessment() {
        assertThatThrownBy(() -> service.open(TENANT, client.getId(), new OpenRequest("A title", null, null, null, null, null, UUID.randomUUID()), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("from an assessment");
    }

    @Test @DisplayName("resolving with a step still open is refused as a bad request and nothing is saved")
    void resolveWithOpenStep() {
        ComplianceCase c = stored("IN_PROGRESS");
        var open = ComplianceCaseStep.of(TENANT, c.getId(), "Collect the ID copy", null, 0, TODAY);
        when(steps.findForCases(any(), any())).thenReturn(List.of(open));

        assertThatThrownBy(() -> service.transition(TENANT, client.getId(), c.getId(), new TransitionRequest("RESOLVE", "Officer appointed and registered"), ME))
                .isInstanceOf(HandyFlowException.class).satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(cases, never()).save(any());
    }

    @Test @DisplayName("a move that is not allowed from the current status is a conflict")
    void badMove() {
        ComplianceCase c = stored("OPEN");
        assertThatThrownBy(() -> service.transition(TENANT, client.getId(), c.getId(), new TransitionRequest("CLOSE", null), ME))
                .isInstanceOf(HandyFlowException.class).satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test @DisplayName("the person who resolved cannot close without a reason, someone else can")
    void closeByOther() {
        ComplianceCase c = stored("IN_PROGRESS");
        c.resolve(ME, "Officer appointed and registered", 0);
        assertThatThrownBy(() -> service.transition(TENANT, client.getId(), c.getId(), new TransitionRequest("CLOSE", null), ME)).isInstanceOf(HandyFlowException.class);
        CaseDetail out = service.transition(TENANT, client.getId(), c.getId(), new TransitionRequest("CLOSE", null), OTHER);
        assertThat(out.summary().status()).isEqualTo("CLOSED");
        assertThat(out.closedBy()).isEqualTo(OTHER);
    }

    @Test @DisplayName("a case of another client is not found")
    void wrongClient() {
        ComplianceCase c = stored("OPEN");
        UUID elsewhere = UUID.randomUUID();
        when(clients.findByIdForTenant(TENANT, elsewhere)).thenReturn(Optional.of(client));
        assertThatThrownBy(() -> service.get(TENANT, elsewhere, c.getId())).hasMessageContaining("Case not found");
    }

    @Test @DisplayName("steps cannot be added to a closed case")
    void closedCase() {
        ComplianceCase c = stored("IN_PROGRESS");
        c.resolve(ME, "Officer appointed and registered", 0);
        c.close(OTHER, null);
        assertThatThrownBy(() -> service.addStep(TENANT, client.getId(), c.getId(), new StepRequest("One more thing", null), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("can no longer change its steps");
    }
}
