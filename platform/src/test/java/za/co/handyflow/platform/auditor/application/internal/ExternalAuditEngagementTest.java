package za.co.handyflow.platform.auditor.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.domain.model.ExternalAuditEngagement;
import za.co.handyflow.platform.auditor.domain.repository.AuditShareRepository;
import za.co.handyflow.platform.auditor.domain.repository.AuditorAccessGrantRepository;
import za.co.handyflow.platform.auditor.domain.repository.ExternalAuditEngagementRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExternalAuditEngagementTest {

    ExternalAuditEngagementRepository repo = mock(ExternalAuditEngagementRepository.class);
    AuditorAccessGrantRepository grants = mock(AuditorAccessGrantRepository.class);
    AuditShareRepository shares = mock(AuditShareRepository.class);
    za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail = mock(za.co.handyflow.platform.audittrail.application.AuditTrailFacade.class);
    ExternalAuditEngagementService service;
    UUID tenant = UUID.randomUUID(), user = UUID.randomUUID();
    TenantId tid;

    @BeforeEach
    void setUp() {
        service = new ExternalAuditEngagementService(repo, grants, shares, trail);
        tid = TenantId.of(tenant);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ExternalAuditEngagement planned() {
        return ExternalAuditEngagement.create(tenant, "FY2026", "Firm LLP", LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 28), "Holdco, Opco", null, user);
    }

    private ExternalAuditEngagement stored(ExternalAuditEngagement e) {
        when(repo.findByTenantIdAndId(tenant, e.getId())).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void needsNameFirmAndAPeriodThatRunsForwards() {
        assertThrows(IllegalArgumentException.class, () -> ExternalAuditEngagement.create(tenant, " ", "F", LocalDate.now(), LocalDate.now(), null, null, user));
        assertThrows(IllegalArgumentException.class, () -> ExternalAuditEngagement.create(tenant, "N", "", LocalDate.now(), LocalDate.now(), null, null, user));
        assertThrows(IllegalArgumentException.class, () -> ExternalAuditEngagement.create(tenant, "N", "F", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, user));
        assertThrows(HandyFlowException.class, () -> service.create(tid, "N", "F", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, user));
    }

    @Test
    void movesForwardOnlyInOrder() {
        var e = planned();
        assertFalse(e.grantsAccess());
        assertThrows(IllegalStateException.class, () -> e.complete(user));
        assertThrows(IllegalStateException.class, () -> e.archive(user));
        e.start();
        assertTrue(e.grantsAccess());
        assertThrows(IllegalStateException.class, e::start);
        assertThrows(IllegalStateException.class, () -> e.archive(user));
        e.complete(user);
        assertFalse(e.grantsAccess());
        assertFalse(e.acceptsInvites());
        e.archive(user);
        assertTrue(e.isArchived());
        assertThrows(IllegalStateException.class, () -> e.update("X", "Y", LocalDate.now(), LocalDate.now(), null, null));
    }

    @Test
    void plannedAndActiveEngagementsTakeInvites() {
        var e = planned();
        assertTrue(e.acceptsInvites());
        e.start();
        assertTrue(e.acceptsInvites());
    }

    @Test
    void archivingRevokesEveryGrantOfTheEngagementAndOnlyThose() {
        var e = stored(planned());
        e.start(); e.complete(user);
        var live = AuditorAccessGrant.createInvite(tenant, "a@firm.test", user);
        var revoked = AuditorAccessGrant.createInvite(tenant, "b@firm.test", user);
        revoked.revoke(user);
        when(grants.findByTenantIdAndEngagementId(tenant, e.getId())).thenReturn(List.of(live, revoked));
        service.archive(tid, e.getId(), user);
        assertEquals("REVOKED", live.getStatus());
        verify(grants, times(1)).save(live);
        verify(grants, never()).save(revoked);
        verify(trail).record(eq(tid), eq("ENGAGEMENT_ARCHIVED"), any(), eq(e.getId()), any(), any());
    }

    @Test
    void cannotArchiveBeforeCompleting() {
        var e = stored(planned());
        e.start();
        var ex = assertThrows(HandyFlowException.class, () -> service.archive(tid, e.getId(), user));
        assertEquals("ENGAGEMENT_STATE", ex.getErrorCode());
        verify(grants, never()).save(any());
    }

    @Test
    void anotherTenantsEngagementIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findByTenantIdAndId(tenant, id)).thenReturn(Optional.empty());
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class, () -> service.start(tid, id));
    }

    @Test
    void manifestListsSharesThatWereLiveDuringTheEngagementOnly() {
        var e = stored(planned());
        e.start(); e.complete(user);
        var during = AuditShare.share(tenant, AuditShare.EVIDENCE, UUID.randomUUID(), "bank statements", user, "Owner");
        var later = AuditShare.share(tenant, AuditShare.EVIDENCE, UUID.randomUUID(), "next year", user, "Owner");
        org.springframework.test.util.ReflectionTestUtils.setField(later, "sharedAt", Instant.now().plusSeconds(3600));
        var before = AuditShare.share(tenant, AuditShare.EVIDENCE, UUID.randomUUID(), "old", user, "Owner");
        org.springframework.test.util.ReflectionTestUtils.setField(before, "sharedAt", Instant.now().minusSeconds(7200));
        org.springframework.test.util.ReflectionTestUtils.setField(before, "withdrawnAt", Instant.now().minusSeconds(3600));
        when(shares.findAllForTenant(tenant)).thenReturn(List.of(during, later, before));
        when(grants.findByTenantIdAndEngagementId(tenant, e.getId())).thenReturn(List.of());
        var m = service.manifest(tid, e.getId());
        assertEquals(1, m.shares().size());
        assertEquals("bank statements", m.shares().get(0).reason());
    }
}
