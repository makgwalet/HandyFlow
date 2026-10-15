package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.internalaudit.application.internal.AuditFollowUpService.Actor;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditFollowUpServiceTest {
    AuditFollowUpLinkRepository links = mock(AuditFollowUpLinkRepository.class);
    AuditFollowUpItemRepository items = mock(AuditFollowUpItemRepository.class);
    AuditEngagementRepository engagements = mock(AuditEngagementRepository.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    AuditFollowUpService service;
    UUID tenant = UUID.randomUUID(), now = UUID.randomUUID(), prior = UUID.randomUUID(), user = UUID.randomUUID();
    TenantId tid;
    Actor actor;

    @BeforeEach
    void setUp() {
        service = new AuditFollowUpService(links, items, engagements, findings, trail);
        tid = TenantId.of(tenant);
        actor = new Actor(user, "Thandi");
        when(links.save(any())).thenAnswer(i -> i.getArgument(0));
        when(items.save(any())).thenAnswer(i -> i.getArgument(0));
        when(engagements.findByTenantAndId(eq(tenant), any())).thenReturn(Optional.of(mock(AuditEngagement.class)));
        when(engagements.findAllForTenant(tenant)).thenReturn(List.of());
        when(items.findByTenantIdAndEngagementId(eq(tenant), any())).thenReturn(List.of());
    }

    private AuditFinding finding() {
        var f = mock(AuditFinding.class);
        when(f.getId()).thenReturn(UUID.randomUUID());
        when(f.getTitle()).thenReturn("A finding");
        when(f.getSeverity()).thenReturn("HIGH");
        return f;
    }

    @Test
    void startMakesOnePendingItemPerEarlierFinding() {
        when(links.findByTenantIdAndEngagementId(tenant, now)).thenReturn(Optional.empty());
        var three = List.of(finding(), finding(), finding());
        when(findings.findByEngagement(tenant, prior)).thenReturn(three);
        service.start(tid, actor, now, prior);
        verify(items, times(3)).save(any(AuditFollowUpItem.class));
        verify(trail).record(eq(tid), eq("AUDIT_FOLLOWUP_STARTED"), eq("AUDIT_ENGAGEMENT"), eq(now), any(), any());
    }

    @Test
    void anEngagementCannotBeStartedTwice() {
        when(links.findByTenantIdAndEngagementId(tenant, now)).thenReturn(Optional.of(AuditFollowUpLink.of(tenant, now, prior, user)));
        var e = assertThrows(HandyFlowException.class, () -> service.start(tid, actor, now, prior));
        assertEquals("FU_EXISTS", e.getErrorCode());
        verify(items, never()).save(any());
    }

    @Test
    void anEarlierEngagementWithNoFindingsIsRefused() {
        when(links.findByTenantIdAndEngagementId(tenant, now)).thenReturn(Optional.empty());
        when(findings.findByEngagement(tenant, prior)).thenReturn(List.of());
        assertEquals("FU_NOTHING", assertThrows(HandyFlowException.class, () -> service.start(tid, actor, now, prior)).getErrorCode());
    }

    @Test
    void anEngagementCannotFollowUpItself() {
        when(links.findByTenantIdAndEngagementId(tenant, now)).thenReturn(Optional.empty());
        var one = List.of(finding());
        when(findings.findByEngagement(tenant, now)).thenReturn(one);
        assertEquals("FU_INVALID", assertThrows(HandyFlowException.class, () -> service.start(tid, actor, now, now)).getErrorCode());
    }

    @Test
    void missingEngagementIdsAreRefused() {
        assertEquals("FU_INVALID", assertThrows(HandyFlowException.class, () -> service.start(tid, actor, null, prior)).getErrorCode());
    }

    @Test
    void assessRecordsAndTrails_andShortNoteIsRefused() {
        var link = AuditFollowUpLink.of(tenant, now, prior, user);
        var f = finding();
        var item = AuditFollowUpItem.pending(tenant, now, f.getId());
        when(links.findByTenantIdAndEngagementId(tenant, now)).thenReturn(Optional.of(link));
        when(items.findByTenantIdAndEngagementIdAndFindingId(tenant, now, f.getId())).thenReturn(Optional.of(item));
        when(findings.findByEngagement(tenant, prior)).thenReturn(List.of(f));

        assertEquals("FU_INVALID", assertThrows(HandyFlowException.class, () -> service.assess(tid, actor, now, f.getId(), "IMPLEMENTED", "ok")).getErrorCode());
        verify(trail, never()).record(any(), eq("AUDIT_FOLLOWUP_ASSESSED"), any(), any(), any(), any());

        service.assess(tid, actor, now, f.getId(), "IMPLEMENTED", "Checked the live approval step.");
        assertEquals("IMPLEMENTED", item.getOutcome());
        verify(trail).record(eq(tid), eq("AUDIT_FOLLOWUP_ASSESSED"), eq("AUDIT_FINDING"), eq(f.getId()), any(), any());
    }

    @Test
    void assessOnAnEngagementThatIsNotAFollowUpIsNotFound() {
        when(links.findByTenantIdAndEngagementId(tenant, now)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.assess(tid, actor, now, UUID.randomUUID(), "IMPLEMENTED", "A proper long note"));
    }

    @Test
    void reportLinesAreEmptyWhenNotAFollowUp() {
        when(links.findByTenantIdAndEngagementId(tenant, now)).thenReturn(Optional.empty());
        assertTrue(service.reportLines(tenant, now).isEmpty());
    }
}
