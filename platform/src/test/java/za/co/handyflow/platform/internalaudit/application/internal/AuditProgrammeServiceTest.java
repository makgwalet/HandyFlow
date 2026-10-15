package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.internalaudit.application.internal.AuditProgrammeService.Actor;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditProgrammeServiceTest {
    AuditProgrammeStepRepository steps = mock(AuditProgrammeStepRepository.class);
    AuditEngagementRepository engagements = mock(AuditEngagementRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    AuditProgrammeService service;
    UUID tenant = UUID.randomUUID(), eng = UUID.randomUUID(), user = UUID.randomUUID();
    TenantId tid;
    Actor actor;
    List<AuditProgrammeStep> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AuditProgrammeService(steps, engagements, findings, trail);
        tid = TenantId.of(tenant);
        actor = new Actor(user, "Thandi");
        var e = mock(AuditEngagement.class);
        when(e.getStatus()).thenReturn("FIELDWORK");
        when(engagements.findByTenantAndId(tenant, eng)).thenReturn(Optional.of(e));
        stored.clear();
        when(steps.findByTenantIdAndEngagementIdOrderByControlCodeAsc(tenant, eng)).thenAnswer(i -> new ArrayList<>(stored));
        when(steps.save(any())).thenAnswer(i -> { var s = (AuditProgrammeStep) i.getArgument(0); if (!stored.contains(s)) stored.add(s); return s; });
        when(steps.findByTenantIdAndEngagementIdAndControlCode(eq(tenant), eq(eng), any())).thenAnswer(i -> stored.stream().filter(s -> s.getControlCode().equals(i.getArgument(2))).findFirst());
    }

    @Test
    void applyingADomainAddsItsControls_andApplyingAgainChangesNothing() {
        var p = service.apply(tid, actor, eng, List.of("CYBER"), null);
        assertEquals(10, p.steps().size());
        stored.get(0).record("COMPLETED", "EFFECTIVE", "Checked and fine here.", user, "T");
        var again = service.apply(tid, actor, eng, List.of("CYBER"), List.of("IT-01"));
        assertEquals(11, again.steps().size());
        assertEquals("COMPLETED", stored.stream().filter(s -> s.getControlCode().equals("CY-01")).findFirst().get().getStatus());
    }

    @Test
    void anUnknownControlOrNothingChosenIsRefused() {
        assertEquals("PRG_INVALID", assertThrows(HandyFlowException.class, () -> service.apply(tid, actor, eng, null, List.of("ZZ-1"))).getErrorCode());
        assertEquals("PRG_INVALID", assertThrows(HandyFlowException.class, () -> service.apply(tid, actor, eng, List.of(), null)).getErrorCode());
        assertTrue(stored.isEmpty());
    }

    @Test
    void recordingTrailsAndFlagsAWeakResultWithoutAFinding() {
        service.apply(tid, actor, eng, null, List.of("IT-01"));
        var p = service.record(tid, actor, eng, "IT-01", "COMPLETED", "INEFFECTIVE", "Three leavers still active.");
        assertEquals(1, p.overall().needingFinding());
        verify(trail).record(eq(tid), eq("AUDIT_PROGRAMME_RECORDED"), eq("AUDIT_ENGAGEMENT"), eq(eng), any(), any());
    }

    @Test
    void aBadResultIsRefusedWithAClearCode() {
        service.apply(tid, actor, eng, null, List.of("IT-01"));
        assertEquals("PRG_INVALID", assertThrows(HandyFlowException.class, () -> service.record(tid, actor, eng, "IT-01", "COMPLETED", null, "Looked at it.")).getErrorCode());
        assertThrows(ResourceNotFoundException.class, () -> service.record(tid, actor, eng, "IT-02", "IN_PROGRESS", null, null));
    }

    @Test
    void aFindingFromAnotherEngagementCannotBeLinked() {
        service.apply(tid, actor, eng, null, List.of("IT-01"));
        var mine = mock(AuditFinding.class);
        when(mine.getId()).thenReturn(UUID.randomUUID());
        when(findings.findByEngagement(tenant, eng)).thenReturn(List.of(mine));
        assertEquals("PRG_INVALID", assertThrows(HandyFlowException.class, () -> service.linkFinding(tid, actor, eng, "IT-01", UUID.randomUUID())).getErrorCode());
        service.linkFinding(tid, actor, eng, "IT-01", mine.getId());
        assertEquals(mine.getId(), stored.get(0).getFindingId());
    }

    @Test
    void aClosedEngagementTakesNoChanges() {
        var closed = mock(AuditEngagement.class);
        when(closed.getStatus()).thenReturn("CLOSED");
        UUID other = UUID.randomUUID();
        when(engagements.findByTenantAndId(tenant, other)).thenReturn(Optional.of(closed));
        assertEquals("PRG_CLOSED", assertThrows(HandyFlowException.class, () -> service.apply(tid, actor, other, List.of("IT"), null)).getErrorCode());
    }

    @Test
    void anEngagementThatDoesNotExistIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(tid, UUID.randomUUID()));
    }
}
