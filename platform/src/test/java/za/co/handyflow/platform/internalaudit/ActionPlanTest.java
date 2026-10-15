package za.co.handyflow.platform.internalaudit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.application.internal.ActionPlanService;
import za.co.handyflow.platform.internalaudit.domain.model.AuditActionPlan;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFinding;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditActionPlanRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ActionPlanTest {

    UUID tenant = UUID.randomUUID(), finding = UUID.randomUUID(), owner = UUID.randomUUID(), doer = UUID.randomUUID(), auditor = UUID.randomUUID();

    private AuditActionPlan plan() {
        return AuditActionPlan.create(tenant, finding, "Reconcile suspense monthly", owner, LocalDate.now().plusDays(30), auditor);
    }

    @Test
    void anActionNeedsADescriptionAndADueDate() {
        assertThrows(IllegalArgumentException.class, () -> AuditActionPlan.create(tenant, finding, " ", owner, LocalDate.now(), auditor));
        assertThrows(IllegalArgumentException.class, () -> AuditActionPlan.create(tenant, finding, "x", owner, null, auditor));
    }

    @Test
    void completionAndValidationNeedRealNotes() {
        AuditActionPlan p = plan();
        assertThrows(IllegalArgumentException.class, () -> p.complete(doer, "done"));
        p.complete(doer, "Reconciliation procedure issued and first run attached");
        assertEquals("COMPLETED", p.getStatus());
        assertThrows(IllegalArgumentException.class, () -> p.validate(auditor, "ok"));
        p.validate(auditor, "Re-performed the March reconciliation; it agrees");
        assertEquals("VALIDATED", p.getStatus());
        assertFalse(p.blocksClosing());
    }

    @Test
    void theCompleterAndTheOwnerCannotValidate() {
        AuditActionPlan p = plan();
        p.complete(doer, "Reconciliation procedure issued and first run attached");
        assertThrows(IllegalStateException.class, () -> p.validate(doer, "Looks fine to me, thanks"));
        assertThrows(IllegalStateException.class, () -> p.validate(owner, "Looks fine to me, thanks"));
        assertThrows(IllegalStateException.class, () -> p.returnForRework(doer, "Not good enough here"));
        assertTrue(p.blocksClosing());
    }

    @Test
    void returnedWorkReopensTheActionAndIsCounted() {
        AuditActionPlan p = plan();
        p.complete(doer, "Reconciliation procedure issued and first run attached");
        p.returnForRework(auditor, "The run does not cover the Sandton account");
        assertEquals("OPEN", p.getStatus());
        assertEquals(1, p.getTimesReturned());
        assertNull(p.getCompletedBy());
        assertEquals("The run does not cover the Sandton account", p.getValidationNote());
    }

    @Test
    void validatingAnOpenActionIsRefusedAndCancelNeedsAReason() {
        AuditActionPlan p = plan();
        assertThrows(IllegalStateException.class, () -> p.validate(auditor, "Nothing was done yet here"));
        assertThrows(IllegalArgumentException.class, () -> p.cancel("no"));
        p.cancel("Superseded by the new close procedure");
        assertFalse(p.blocksClosing());
        assertThrows(IllegalStateException.class, () -> p.cancel("Again for good measure"));
    }

    @Test
    void overdueMeansPastDueAndNotYetValidatedOrCancelled() {
        AuditActionPlan p = AuditActionPlan.create(tenant, finding, "x", owner, LocalDate.now().minusDays(1), auditor);
        assertTrue(p.isOverdue(LocalDate.now()));
        p.complete(doer, "Reconciliation procedure issued and first run attached");
        assertTrue(p.isOverdue(LocalDate.now()));
        p.validate(auditor, "Re-performed the March reconciliation; it agrees");
        assertFalse(p.isOverdue(LocalDate.now()));
    }

    // ── service ─────────────────────────────────────────────────────────────
    AuditActionPlanRepository repo = mock(AuditActionPlanRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    UserRepository users = mock(UserRepository.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    ActionPlanService service;
    TenantId t;

    @BeforeEach
    void setUp() {
        service = new ActionPlanService(repo, findings, users, trail);
        t = TenantId.of(tenant);
    }

    @Test
    void onlyTheOwnerOrAManagerMarksAnActionComplete() {
        AuditActionPlan p = plan();
        when(repo.findByTenantAndId(tenant, p.getId())).thenReturn(Optional.of(p));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.complete(t, p.getId(), "Reconciliation procedure issued", doer, false));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        service.complete(t, p.getId(), "Reconciliation procedure issued and first run attached", owner, false);
        assertEquals("COMPLETED", p.getStatus());
        verify(trail).record(eq(t), eq("ACTION_PLAN_COMPLETED"), eq("ACTION_PLAN"), eq(p.getId()), any(), any());
    }

    @Test
    void theServiceTurnsRuleBreaksIntoClearErrorsAndLeavesNoTrail() {
        AuditActionPlan p = plan();
        when(repo.findByTenantAndId(tenant, p.getId())).thenReturn(Optional.of(p));
        p.complete(doer, "Reconciliation procedure issued and first run attached");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.validate(t, p.getId(), "Looks fine to me, thanks", doer));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verifyNoInteractions(trail);
    }

    @Test
    void noActionsCanBeAddedToAClosedFinding() {
        AuditFinding f = mock(AuditFinding.class);
        when(f.getStatus()).thenReturn("CLOSED");
        when(findings.findByTenantAndId(tenant, finding)).thenReturn(Optional.of(f));
        var req = new za.co.handyflow.platform.internalaudit.dto.ActionPlanDtos.CreateRequest("Do it", owner, LocalDate.now().plusDays(5));
        assertEquals("FINDING_CLOSED", assertThrows(HandyFlowException.class, () -> service.create(t, finding, req, auditor)).getErrorCode());
        verify(repo, never()).save(any());
    }
}
