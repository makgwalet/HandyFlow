package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.SamplingDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvSamplingServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock private TrainProvSamplingRuleRepository rules;
    @Mock private TrainProvSamplingRuleVersionRepository versions;
    @Mock private TrainProvSamplingDrawRepository draws;
    @Mock private TrainProvSamplingDrawItemRepository items;
    @Mock private TrainProvSamplingActionRepository actions;
    @Mock private TrainProvAssessmentAttemptRepository attempts;
    @Mock private TrainProvAssessmentDecisionRepository decisions;
    @Mock private TrainProvAssessmentInstrumentRepository instruments;
    @Mock private TrainProvAssessmentModerationRepository moderations;
    @Mock private TrainProvProgrammeRepository programmes;
    @Mock private TrainProvCohortRepository cohorts;
    @Mock private TrainProvLearnerEnrolmentRepository enrolments;
    @Mock private TrainProvLearnerRepository learners;
    @Mock private TrainProvPractitionerRepository practitioners;
    @Mock private TrainProvNumberGenerator numbers;
    @InjectMocks private TrainProvSamplingService service;

    private static SettingsRequest settings(String rationale) {
        return new SettingsRequest(TODAY, "PERCENTAGE", new BigDecimal("10"), null, null, "RANDOM", null, null, null, null, rationale);
    }

    @Test
    void ruleNeedsAReasonForItsSettings() {
        RuleRequest r = new RuleRequest("All", "ALL", null, null, null, settings("short"));
        assertThatThrownBy(() -> service.createRule(TENANT, r, USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("Say why the rule is set this way");
        verify(rules, never()).save(any());
    }

    @Test
    void secondLiveRuleForTheSameScopeIsRefused() {
        when(rules.findLiveForScope(TENANT, "ALL", null, null, null)).thenReturn(Optional.of(TrainProvSamplingRule.create(TENANT, "SMR-00001", "Existing", "ALL", null, null, null, USER)));
        RuleRequest r = new RuleRequest("Another", "ALL", null, null, null, settings("Agreed at the quality meeting"));
        assertThatThrownBy(() -> service.createRule(TENANT, r, USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already a live rule");
    }

    @Test
    void newVersionMustStartAfterTheLatest() {
        TrainProvSamplingRule rule = TrainProvSamplingRule.create(TENANT, "SMR-00001", "All", "ALL", null, null, null, USER);
        when(rules.findForTenant(TENANT, rule.getId())).thenReturn(Optional.of(rule));
        TrainProvSamplingRuleVersion v1 = TrainProvSamplingRuleVersion.create(TENANT, rule.getId(), 1, TODAY, "PERCENTAGE", new BigDecimal("10"), null, null, "RANDOM", null, null, null, null,
                "Agreed at the quality meeting", USER);
        when(versions.forRule(TENANT, rule.getId())).thenReturn(List.of(v1));
        assertThatThrownBy(() -> service.addVersion(TENANT, rule.getId(), settings("A change that is not later"), USER, TODAY)).isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("must start after");
        verify(versions, never()).save(any());
    }

    @Test
    void drawCannotEndInTheFuture() {
        TrainProvSamplingRule rule = TrainProvSamplingRule.create(TENANT, "SMR-00001", "All", "ALL", null, null, null, USER);
        when(rules.findForTenant(TENANT, rule.getId())).thenReturn(Optional.of(rule));
        DrawRequest r = new DrawRequest(rule.getId(), TODAY.minusDays(5), TODAY.plusDays(1), null, null);
        assertThatThrownBy(() -> service.draw(TENANT, r, USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(draws, never()).save(any());
    }

    @Test
    void drawerNeedsToConfirmWhenClosingTheirOwnDraw() {
        TrainProvSamplingDraw d = TrainProvSamplingDraw.create(TENANT, "SMD-00001", UUID.randomUUID(), UUID.randomUUID(), "snapshot", TODAY, TODAY.minusDays(5), TODAY, null, null, null, 7L, 3, 1, 1, USER);
        when(draws.findForTenant(TENANT, d.getId())).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.close(TENANT, d.getId(), new CloseRequest("RESULTS_ACCEPTED", "Everything agrees with the assessor", false), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("second person");
        assertThat(d.isOpen()).isTrue();
    }

    @Test
    void drawStaysOpenWhileAPickedResultHasNoModeration() {
        TrainProvSamplingDraw d = TrainProvSamplingDraw.create(TENANT, "SMD-00001", UUID.randomUUID(), UUID.randomUUID(), "snapshot", TODAY, TODAY.minusDays(5), TODAY, null, null, null, 7L, 3, 1, 1, USER);
        when(draws.findForTenant(TENANT, d.getId())).thenReturn(Optional.of(d));
        TrainProvSamplingDrawItem item = TrainProvSamplingDrawItem.create(TENANT, d.getId(), UUID.randomUUID(), UUID.randomUUID(), 0, null, true, "RANDOM", null);
        when(items.forDraw(TENANT, d.getId())).thenReturn(List.of(item));
        when(moderations.findForAttempt(TENANT, item.getAttemptId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.close(TENANT, d.getId(), new CloseRequest("RESULTS_ACCEPTED", "Everything agrees with the assessor", false), OTHER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no moderation yet");
        assertThat(d.isOpen()).isTrue();
    }

    @Test
    void moderatedDrawIsNotVoided() {
        TrainProvSamplingDraw d = TrainProvSamplingDraw.create(TENANT, "SMD-00001", UUID.randomUUID(), UUID.randomUUID(), "snapshot", TODAY, TODAY.minusDays(5), TODAY, null, null, null, 7L, 3, 1, 1, USER);
        when(draws.findForTenant(TENANT, d.getId())).thenReturn(Optional.of(d));
        TrainProvSamplingDrawItem item = TrainProvSamplingDrawItem.create(TENANT, d.getId(), UUID.randomUUID(), UUID.randomUUID(), 0, null, true, "RANDOM", null);
        when(items.forDraw(TENANT, d.getId())).thenReturn(List.of(item));
        when(moderations.findForAttempt(TENANT, item.getAttemptId())).thenReturn(Optional.of(mock(TrainProvAssessmentModeration.class)));
        assertThatThrownBy(() -> service.voidDraw(TENANT, d.getId(), "Drawn by mistake today", USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already been moderated");
    }
}
