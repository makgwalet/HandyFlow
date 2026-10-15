package za.co.handyflow.platform.auditor.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.domain.repository.AuditShareRepository;
import za.co.handyflow.platform.auditor.domain.repository.AuditorAccessGrantRepository;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.controls.dto.ControlExceptionResponse;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** One tenant's auditor against another tenant's data; expired and revoked grants; nothing is visible until shared. */
class AuditorPortalIsolationTest {

    AuditorAccessGrantRepository grants = mock(AuditorAccessGrantRepository.class);
    EvidenceFacade evidence = mock(EvidenceFacade.class);
    ControlExceptionFacade exceptions = mock(ControlExceptionFacade.class);
    AuditFindingRepository findings = mock(AuditFindingRepository.class);
    AuditShareRepository shares = mock(AuditShareRepository.class);
    za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail = mock(za.co.handyflow.platform.audittrail.application.AuditTrailFacade.class);
    AuditorPortalDataService service;

    UUID auditor = UUID.randomUUID(), tenantA = UUID.randomUUID(), tenantB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuditorPortalDataService(grants, evidence, exceptions, findings, shares, trail);
    }

    private AuditorAccessGrant grantFor(UUID tenant) {
        AuditorAccessGrant g = AuditorAccessGrant.createInvite(tenant, "a@firm.test", UUID.randomUUID());
        g.acceptInvite(auditor);
        when(grants.findByTenantId(tenant)).thenReturn(List.of(g));
        return g;
    }

    private static AuditShare shareOf(UUID tenant, String type, UUID item) {
        return AuditShare.share(tenant, type, item, "for the audit", UUID.randomUUID(), "Owner");
    }

    @Test
    void nothingIsVisibleUntilSomethingIsShared() {
        grantFor(tenantA);
        when(shares.findLive(tenantA, AuditShare.EVIDENCE)).thenReturn(List.of());
        when(evidence.listByIds(any(), any())).thenReturn(List.of());
        assertTrue(service.getEvidence(auditor, tenantA).isEmpty());
        verify(evidence, never()).listAllForTenant(any());
        verify(evidence).listByIds(eq(TenantId.of(tenantA)), eq(Set.<UUID>of()));
    }

    @Test
    void anAuditorViewingEvidenceLeavesATrailEntryInTheirName() {
        grantFor(tenantA);
        when(shares.findLive(any(), any())).thenReturn(List.of());
        when(evidence.listByIds(any(), any())).thenReturn(List.of());
        service.getEvidence(auditor, tenantA);
        verify(trail).recordAs(eq(TenantId.of(tenantA)), eq("AUDITOR"), eq(auditor), any(), eq("AUDITOR_VIEWED_EVIDENCE"), eq("TENANT"), eq(tenantA), any(), any());
    }

    @Test
    void aRefusedAuditorLeavesNoViewEntry() {
        when(grants.findByTenantId(tenantB)).thenReturn(List.of());
        assertThrows(HandyFlowException.class, () -> service.getEvidence(auditor, tenantB));
        verifyNoInteractions(trail);
    }

    @Test
    void onlyTheSharedEvidenceIsAskedFor() {
        grantFor(tenantA);
        UUID shared = UUID.randomUUID();
        when(shares.findLive(tenantA, AuditShare.EVIDENCE)).thenReturn(List.of(shareOf(tenantA, AuditShare.EVIDENCE, shared)));
        EvidenceResponse r = new EvidenceResponse(shared, "bank.pdf", "application/pdf", 10L, "BANK_STATEMENT", "ACTIVE", "Owner", Instant.now());
        when(evidence.listByIds(TenantId.of(tenantA), Set.of(shared))).thenReturn(List.of(r));
        assertEquals(List.of(r), service.getEvidence(auditor, tenantA));
    }

    @Test
    void onlyTheSharedControlExceptionsAreAskedFor() {
        grantFor(tenantA);
        UUID shared = UUID.randomUUID();
        when(shares.findLive(tenantA, AuditShare.CONTROL_EXCEPTION)).thenReturn(List.of(shareOf(tenantA, AuditShare.CONTROL_EXCEPTION, shared)));
        when(exceptions.listByIds(TenantId.of(tenantA), Set.of(shared))).thenReturn(List.<ControlExceptionResponse>of());
        service.getControlExceptions(auditor, tenantA);
        verify(exceptions, never()).listAll(any());
        verify(exceptions).listByIds(TenantId.of(tenantA), Set.of(shared));
    }

    @Test
    void anAuditorWithAGrantOnOneTenantGetsNothingFromAnother() {
        grantFor(tenantA);
        when(grants.findByTenantId(tenantB)).thenReturn(List.of());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.getEvidence(auditor, tenantB));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        verifyNoInteractions(evidence, shares);
    }

    @Test
    void anotherAuditorsGrantDoesNotCount() {
        AuditorAccessGrant other = AuditorAccessGrant.createInvite(tenantA, "x@firm.test", UUID.randomUUID());
        other.acceptInvite(UUID.randomUUID());
        when(grants.findByTenantId(tenantA)).thenReturn(List.of(other));
        assertThrows(HandyFlowException.class, () -> service.getControlExceptions(auditor, tenantA));
    }

    @Test
    void anExpiredGrantIsRefusedOnEveryCall() {
        AuditorAccessGrant g = grantFor(tenantA);
        ReflectionTestUtils.setField(g, "expiresAt", Instant.now().minus(Duration.ofMinutes(1)));
        when(grants.findByPortalUserId(auditor)).thenReturn(List.of(g));
        assertThrows(HandyFlowException.class, () -> service.getEvidence(auditor, tenantA));
        assertThrows(HandyFlowException.class, () -> service.getControlExceptions(auditor, tenantA));
        assertThrows(HandyFlowException.class, () -> service.getSharedFindings(auditor, tenantA));
        assertTrue(service.getMyTenants(auditor).isEmpty());
    }

    @Test
    void aRevokedGrantIsRefused() {
        AuditorAccessGrant g = grantFor(tenantA);
        g.revoke(UUID.randomUUID());
        assertThrows(HandyFlowException.class, () -> service.getEvidence(auditor, tenantA));
    }
}
