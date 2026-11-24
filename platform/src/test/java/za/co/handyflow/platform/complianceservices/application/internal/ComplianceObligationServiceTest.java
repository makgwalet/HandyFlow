package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceObligation;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceObligationVersion;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ObligationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
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
class ComplianceObligationServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();

    @Mock ComplianceObligationRepository obligations;
    @Mock ComplianceObligationVersionRepository versions;
    @Mock ComplianceObligationConditionRepository conditions;
    @Mock ComplianceObligationEvidenceRepository evidence;

    private ComplianceObligationService service;
    private ComplianceObligation obligation;

    @BeforeEach
    void setUp() {
        service = new ComplianceObligationService(obligations, versions, conditions, evidence);
        obligation = ComplianceObligation.create(TENANT, "CIPC-AR", AUTHOR);
        when(obligations.findByIdForTenant(TENANT, obligation.getId())).thenReturn(Optional.of(obligation));
    }

    private ComplianceObligationVersion approved(int number, LocalDate from, LocalDate to) {
        ComplianceObligationVersion v = ComplianceObligationVersion.draft(TENANT, obligation.getId(), number, AUTHOR);
        v.edit(new ComplianceObligationVersion.Content("AR", null, "Act s33", null, null, "ANNUAL", "NONE", null, null, null, null, null,
                "HIGH", "CLIENT", null, number > 1 ? "changed" : null), AUTHOR);
        v.submit(AUTHOR);
        v.approve(REVIEWER, from, null, null);
        if (to != null) v.endOn(to);
        return v;
    }

    private ComplianceObligationVersion inReview(int number) {
        ComplianceObligationVersion v = ComplianceObligationVersion.draft(TENANT, obligation.getId(), number, AUTHOR);
        v.edit(new ComplianceObligationVersion.Content("AR", null, "Act s33", null, null, "ANNUAL", "NONE", null, null, null, null, null,
                "HIGH", "CLIENT", null, "changed"), AUTHOR);
        v.submit(AUTHOR);
        return v;
    }

    @Test @DisplayName("a code that is already used is refused")
    void duplicateCode() {
        when(obligations.findByCode(TENANT, "CIPC-AR")).thenReturn(Optional.of(obligation));
        assertThatThrownBy(() -> service.create(TENANT, new CreateObligationRequest("cipc-ar", null), AUTHOR))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already exists");
        verify(obligations, never()).save(any());
    }

    @Test @DisplayName("approving version 2 ends version 1 the day before")
    void approveEndsPrevious() {
        ComplianceObligationVersion v1 = approved(1, LocalDate.of(2025, 1, 1), null);
        ComplianceObligationVersion v2 = inReview(2);
        when(versions.findOne(TENANT, obligation.getId(), v2.getId())).thenReturn(Optional.of(v2));
        when(versions.findForObligation(TENANT, obligation.getId())).thenReturn(List.of(v1, v2));
        when(conditions.findForVersions(any(), any())).thenReturn(List.of());
        when(evidence.findForVersions(any(), any())).thenReturn(List.of());
        service.approve(TENANT, obligation.getId(), v2.getId(), new ApproveRequest(LocalDate.of(2026, 7, 1), null, null), REVIEWER);
        assertThat(v1.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(v2.isApproved()).isTrue();
    }

    @Test @DisplayName("a new version cannot start on or before the day the last one started")
    void overlap() {
        ComplianceObligationVersion v1 = approved(1, LocalDate.of(2026, 7, 1), null);
        ComplianceObligationVersion v2 = inReview(2);
        when(versions.findOne(TENANT, obligation.getId(), v2.getId())).thenReturn(Optional.of(v2));
        when(versions.findForObligation(TENANT, obligation.getId())).thenReturn(List.of(v1, v2));
        assertThatThrownBy(() -> service.approve(TENANT, obligation.getId(), v2.getId(), new ApproveRequest(LocalDate.of(2026, 7, 1), null, null), REVIEWER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("after the one before");
    }

    @Test @DisplayName("a second draft cannot be started while one is open")
    void oneDraftAtATime() {
        when(versions.findForObligation(TENANT, obligation.getId())).thenReturn(List.of(approved(1, LocalDate.of(2025, 1, 1), null), inReview(2)));
        assertThatThrownBy(() -> service.newVersion(TENANT, obligation.getId(), AUTHOR))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already a draft");
    }

    @Test @DisplayName("a new draft is version 2 and copies version 1")
    void newDraft() {
        ComplianceObligationVersion v1 = approved(1, LocalDate.of(2025, 1, 1), null);
        when(versions.findForObligation(TENANT, obligation.getId())).thenReturn(List.of(v1));
        when(conditions.findForVersions(any(), any())).thenReturn(List.of());
        when(evidence.findForVersions(any(), any())).thenReturn(List.of());
        service.newVersion(TENANT, obligation.getId(), AUTHOR);
        org.mockito.ArgumentCaptor<ComplianceObligationVersion> cap = org.mockito.ArgumentCaptor.forClass(ComplianceObligationVersion.class);
        verify(versions).save(cap.capture());
        assertThat(cap.getValue().getVersionNumber()).isEqualTo(2);
        assertThat(cap.getValue().getName()).isEqualTo("AR");
        assertThat(cap.getValue().isDraft()).isTrue();
    }

    @Test @DisplayName("an approved version cannot be saved over")
    void lockedDraft() {
        ComplianceObligationVersion v1 = approved(1, LocalDate.of(2025, 1, 1), null);
        when(versions.findOne(TENANT, obligation.getId(), v1.getId())).thenReturn(Optional.of(v1));
        assertThatThrownBy(() -> service.saveDraft(TENANT, obligation.getId(), v1.getId(),
                new SaveVersionRequest("X", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null), AUTHOR))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Only a draft");
    }

    @Test @DisplayName("retiring ends the open-ended version and leaves its text alone")
    void retire() {
        ComplianceObligationVersion v1 = approved(1, LocalDate.of(2025, 1, 1), null);
        when(versions.findForObligation(TENANT, obligation.getId())).thenReturn(List.of(v1));
        when(conditions.findForVersions(any(), any())).thenReturn(List.of());
        when(evidence.findForVersions(any(), any())).thenReturn(List.of());
        service.retire(TENANT, obligation.getId(), LocalDate.of(2026, 12, 31), REVIEWER);
        assertThat(v1.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(v1.getName()).isEqualTo("AR");
    }

    @Test @DisplayName("nothing to retire when no version is open-ended")
    void retireNothing() {
        when(versions.findForObligation(TENANT, obligation.getId())).thenReturn(List.of(approved(1, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))));
        assertThatThrownBy(() -> service.retire(TENANT, obligation.getId(), null, REVIEWER)).isInstanceOf(HandyFlowException.class);
    }
}
