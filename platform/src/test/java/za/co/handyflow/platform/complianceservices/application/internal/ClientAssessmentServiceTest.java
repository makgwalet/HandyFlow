package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.AssessmentDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAssessmentServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceClientObligationRepository assignments;
    @Mock ComplianceObligationRepository obligations;
    @Mock ComplianceObligationVersionRepository versions;
    @Mock ComplianceObligationEvidenceRepository obligationEvidence;
    @Mock ClientComplianceDocumentRepository documents;
    @Mock ComplianceAssessmentRepository assessments;
    @Mock ComplianceAssessmentItemRepository items;
    @Mock ComplianceAssessmentEvidenceRepository evidence;
    @Mock ComplianceAssessmentWeightsRepository weightsRepo;

    private ClientAssessmentService service;
    private ComplianceClient client;
    private ComplianceObligation obligation;
    private ComplianceObligationVersion version;
    private final List<ComplianceAssessmentItem> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ClientAssessmentService(clients, assignments, obligations, versions, obligationEvidence, documents, assessments, items, evidence, weightsRepo);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        obligation = ComplianceObligation.create(TENANT, "CIPC-AR", USER);
        version = ComplianceObligationVersion.draft(TENANT, obligation.getId(), 1, USER);
        version.edit(new ComplianceObligationVersion.Content("Annual return", null, "Act s33", null, null, "ANNUAL", "NONE", null, null, null, null,
                null, "HIGH", "CLIENT", null, null), USER);
    }

    private ComplianceClientObligation assigned(String state) {
        ApplicabilityRules.Evaluation e = new ApplicabilityRules.Evaluation(state, List.of(), false);
        return ComplianceClientObligation.suggested(TENANT, client.getId(), obligation.getId(), version.getId(), 1, e);
    }

    @Test @DisplayName("starting with no assigned obligations is refused, pointing at the suggestions")
    void noObligations() {
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.start(TENANT, client.getId(), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Update the suggestions");
        verify(assessments, never()).save(any());
    }

    @Test @DisplayName("a second assessment cannot start while one is in progress")
    void oneAtATime() {
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of(assigned("APPLICABLE")));
        when(assessments.findDraft(TENANT, client.getId())).thenReturn(Optional.of(ComplianceAssessment.start(TENANT, client.getId(), TODAY, AssessmentRules.Weights.defaults(), USER)));
        assertThatThrownBy(() -> service.start(TENANT, client.getId(), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already in progress");
        verify(assessments, never()).save(any());
    }

    @Test @DisplayName("completing is refused while an applicable obligation has not been judged")
    void completeBlocked() {
        ComplianceAssessment a = ComplianceAssessment.start(TENANT, client.getId(), TODAY, AssessmentRules.Weights.defaults(), USER);
        ComplianceAssessmentItem item = ComplianceAssessmentItem.of(TENANT, a.getId(), obligation.getId(), version.getId(), 1, "CIPC-AR", "Annual return", "HIGH", "APPLICABLE", "MET");
        when(assessments.findOne(TENANT, client.getId(), a.getId())).thenReturn(Optional.of(a));
        when(items.findForAssessment(TENANT, a.getId())).thenReturn(List.of(item));
        assertThatThrownBy(() -> service.complete(TENANT, client.getId(), a.getId(), new CompleteRequest(null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("CIPC-AR");
        assertThat(a.isDraft()).isTrue();
    }

    @Test @DisplayName("a completed assessment cannot be judged again")
    void frozen() {
        ComplianceAssessment a = ComplianceAssessment.start(TENANT, client.getId(), TODAY, AssessmentRules.Weights.defaults(), USER);
        a.complete(new AssessmentRules.Score(null, 0, 0), null, USER);
        when(assessments.findOne(TENANT, client.getId(), a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.judge(TENANT, client.getId(), a.getId(), obligation.getId(), new JudgeRequest("YES", null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("completed");
        verify(items, never()).save(any());
    }

    @Test @DisplayName("an invalid judgement is a 400, not a server error")
    void invalidJudgement() {
        ComplianceAssessment a = ComplianceAssessment.start(TENANT, client.getId(), TODAY, AssessmentRules.Weights.defaults(), USER);
        ComplianceAssessmentItem item = ComplianceAssessmentItem.of(TENANT, a.getId(), obligation.getId(), version.getId(), 1, "CIPC-AR", "Annual return", "HIGH", "APPLICABLE", "MET");
        when(assessments.findOne(TENANT, client.getId(), a.getId())).thenReturn(Optional.of(a));
        when(items.findOne(TENANT, a.getId(), obligation.getId())).thenReturn(Optional.of(item));
        assertThatThrownBy(() -> service.judge(TENANT, client.getId(), a.getId(), obligation.getId(), new JudgeRequest("NO", null, "short"), USER))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("weights that fall as risk rises are refused")
    void badWeights() {
        assertThatThrownBy(() -> service.setWeights(TENANT, new WeightsRequest(5, 2, 4, 8), USER)).isInstanceOf(HandyFlowException.class);
        verify(weightsRepo, never()).save(any());
    }

    @Test @DisplayName("with no saved weights the defaults are reported as defaults")
    void defaultWeights() {
        when(weightsRepo.findForTenant(TENANT)).thenReturn(Optional.empty());
        WeightsView w = service.weights(TENANT);
        assertThat(w.isDefault()).isTrue();
        assertThat(w.critical()).isEqualTo(8);
    }
}
