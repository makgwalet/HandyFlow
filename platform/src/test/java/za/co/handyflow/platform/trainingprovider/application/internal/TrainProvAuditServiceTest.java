package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.AuditDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvAuditServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID LEAD = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock private TrainProvAuditTemplateRepository templates;
    @Mock private TrainProvAuditTemplateVersionRepository templateVersions;
    @Mock private TrainProvAuditRepository audits;
    @Mock private TrainProvAuditItemRepository items;
    @Mock private TrainProvAuditActionRepository actions;
    @Mock private TrainProvDeliverySiteRepository sites;
    @Mock private TrainProvCohortRepository cohorts;
    @Mock private TrainProvProgrammeRepository programmes;
    @Mock private TrainProvNumberGenerator numbers;
    @InjectMocks private TrainProvAuditService service;

    private TrainProvAudit audit;
    private TrainProvAuditItem item;

    @BeforeEach
    void setUp() {
        audit = TrainProvAudit.create(TENANT, "AUD-00001", UUID.randomUUID(), "Q1 site audit", "GENERAL", null, null, null, TODAY, LEAD);
        audit.start(TODAY);
        item = TrainProvAuditItem.of(TENANT, audit.getId(), 1, "Is the register kept?");
        lenient().when(audits.findForTenant(TENANT, audit.getId())).thenReturn(Optional.of(audit));
        lenient().when(items.findForTenant(TENANT, item.getId())).thenReturn(Optional.of(item));
        lenient().when(items.forAudit(TENANT, audit.getId())).thenReturn(List.of(item));
        lenient().when(actions.forAudit(TENANT, audit.getId())).thenReturn(List.of());
        lenient().when(templateVersions.findForTenant(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void anAnswerOtherThanNoIssueNeedsTheAuditorsWords() {
        assertThatThrownBy(() -> service.answer(TENANT, item.getId(), new AnswerRequest("ISSUE", "short"), LEAD, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("at least 10");
        AuditDetail d = service.answer(TENANT, item.getId(), new AnswerRequest("ISSUE", "The register was not signed on two days"), LEAD, TODAY);
        assertThat(d.items().get(0).outcome()).isEqualTo("ISSUE");
        assertThat(d.note()).contains("does not show that anyone or anything meets a standard");
    }

    @Test
    void cannotCompleteWhileAnItemIsUnanswered() {
        assertThatThrownBy(() -> service.complete(TENANT, audit.getId(), new CompleteRequest("All items looked at", false, null), OTHER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not answered yet");
    }

    @Test
    void cannotCompleteWhileAnIssueHasNoAction() {
        item.answer("ISSUE", "The register was not signed on two days", LEAD);
        assertThatThrownBy(() -> service.complete(TENANT, audit.getId(), new CompleteRequest("All items looked at", false, null), OTHER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no corrective action");
    }

    @Test
    void anIssueWithAnOpenActionLetsASecondPersonComplete() {
        item.answer("ISSUE", "The register was not signed on two days", LEAD);
        TrainProvAuditAction a = TrainProvAuditAction.create(TENANT, audit.getId(), item.getId(), "Sign the register daily", "Thandi", TODAY.plusDays(14), TODAY, LEAD);
        when(actions.forAudit(TENANT, audit.getId())).thenReturn(List.of(a));
        AuditDetail d = service.complete(TENANT, audit.getId(), new CompleteRequest("All items looked at", false, null), OTHER, TODAY);
        assertThat(d.status()).isEqualTo("COMPLETED");
        assertThat(d.completedAlone()).isFalse();
    }

    @Test
    void theLeadCompletingAloneNeedsAWrittenReason() {
        item.answer("NO_ISSUE", null, LEAD);
        assertThatThrownBy(() -> service.complete(TENANT, audit.getId(), new CompleteRequest("All items looked at", false, null), LEAD, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("A second person should complete this audit");
        assertThatThrownBy(() -> service.complete(TENANT, audit.getId(), new CompleteRequest("All items looked at", true, "short"), LEAD, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("at least 10");
        AuditDetail d = service.complete(TENANT, audit.getId(), new CompleteRequest("All items looked at", true, "Only one qualified person on site"), LEAD, TODAY);
        assertThat(d.completedAlone()).isTrue();
        assertThat(d.completedAloneReason()).isEqualTo("Only one qualified person on site");
    }

    @Test
    void anActionIsVerifiedByASecondPersonOrWithAWrittenReason() {
        TrainProvAuditAction a = TrainProvAuditAction.create(TENANT, audit.getId(), item.getId(), "Sign the register daily", "Thandi", TODAY.plusDays(14), TODAY, LEAD);
        a.markDone(TODAY, "Register now signed every day", LEAD);
        when(actions.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.verify(TENANT, a.getId(), new VerifyRequest(false, null), LEAD, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("second person");
        AuditDetail d = service.verify(TENANT, a.getId(), new VerifyRequest(false, null), OTHER, TODAY);
        assertThat(a.getVerifiedBy()).isEqualTo(OTHER);
        assertThat(d).isNotNull();
    }

    @Test
    void anActionPastItsDayReadsAsOverdueButIsNeverAFinding() {
        TrainProvAuditAction a = TrainProvAuditAction.create(TENANT, audit.getId(), null, "Sign the register daily", "Thandi", TODAY, TODAY, LEAD);
        when(actions.forAudit(TENANT, audit.getId())).thenReturn(List.of(a));
        AuditDetail d = service.get(TENANT, audit.getId(), TODAY.plusDays(3));
        assertThat(d.actions().get(0).overdue()).isTrue();
    }

    @Test
    void nothingIsEverDeleted() {
        service.cancel(TENANT, audit.getId(), "The site closed before the audit", TODAY);
        verify(audits, never()).delete(any(TrainProvAudit.class));
        verify(items, never()).delete(any(TrainProvAuditItem.class));
    }
}
