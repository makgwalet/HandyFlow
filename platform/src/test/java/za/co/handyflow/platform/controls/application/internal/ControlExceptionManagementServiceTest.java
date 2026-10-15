package za.co.handyflow.platform.controls.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.controls.domain.model.ControlException;
import za.co.handyflow.platform.controls.domain.repository.ControlExceptionRepository;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ControlExceptionManagementServiceTest {
    ControlExceptionRepository repo = mock(ControlExceptionRepository.class);
    EvidenceFacade evidence = mock(EvidenceFacade.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    UserRepository users = mock(UserRepository.class);
    ControlExceptionManagementService service;
    UUID tenant = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();
    TenantId tid;
    LocalDate today = LocalDate.of(2026, 10, 15);

    @BeforeEach
    void setUp() {
        service = new ControlExceptionManagementService(repo, evidence, trail, users);
        tid = TenantId.of(tenant);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ControlException stored() {
        var e = ControlException.raise(tenant, "scm", "THREE_WAY_MATCH", "INVOICE", UUID.randomUUID(), "WARNING", "Mismatch");
        when(repo.findByTenantIdAndId(tenant, e.getId())).thenReturn(Optional.of(e));
        when(evidence.listFor(eq(tid), any(), any(), any())).thenReturn(List.of());
        return e;
    }

    private static EvidenceResponse file() {
        return new EvidenceResponse(UUID.randomUUID(), "proof.pdf", "application/pdf", 10L, "DOCUMENT", "ACTIVE", "Alice", Instant.now());
    }

    @Test
    void seriousCannotResolveWithoutEvidence() {
        var e = stored();
        service.rate(tid, e.getId(), alice, "Alice", "HIGH", "Large and repeated", today);
        service.respond(tid, e.getId(), alice, "Alice", "Being renegotiated with supplier", today);
        var ex = assertThrows(HandyFlowException.class, () -> service.resolve(tid, e.getId(), bob, "Bob", "Credit note received", today.plusDays(30), today));
        assertEquals("CONTROL_EXCEPTION_EVIDENCE_REQUIRED", ex.getErrorCode());
        assertEquals("OPEN", e.getStatus());
    }

    @Test
    void seriousResolvesOnceEvidenceAttached() {
        var e = stored();
        service.rate(tid, e.getId(), alice, "Alice", "HIGH", "Large and repeated", today);
        service.respond(tid, e.getId(), alice, "Alice", "Being renegotiated with supplier", today);
        when(evidence.listFor(tid, "controls", "CONTROL_EXCEPTION", e.getId())).thenReturn(List.of(file()));
        var d = service.resolve(tid, e.getId(), bob, "Bob", "Credit note received", today.plusDays(30), today);
        assertEquals("RESOLVED", d.status());
        assertEquals(1, d.evidenceCount());
    }

    @Test
    void domainRulesBecomeConflictsAndEveryStepIsTrailed() {
        var e = stored();
        var ex = assertThrows(HandyFlowException.class, () -> service.resolve(tid, e.getId(), bob, "Bob", "Credit note received", null, today));
        assertEquals("CONTROL_EXCEPTION_STATE", ex.getErrorCode());
        service.rate(tid, e.getId(), alice, "Alice", "LOW", "Small one-off amount", today);
        var bad = assertThrows(HandyFlowException.class, () -> service.rate(tid, e.getId(), alice, "Alice", "NOPE", "Small one-off amount", today));
        assertEquals("CONTROL_EXCEPTION_INVALID", bad.getErrorCode());
        verify(trail).record(eq(tid), eq("CONTROL_EXCEPTION_RATED"), eq("CONTROL_EXCEPTION"), eq(e.getId()), any(), any());
    }

    @Test
    void assignRejectsPersonFromAnotherCompany() {
        var e = stored();
        when(users.findByIdAndTenantId(bob, tid)).thenReturn(Optional.empty());
        var ex = assertThrows(HandyFlowException.class, () -> service.assign(tid, e.getId(), bob, today.plusDays(7), today));
        assertEquals("CONTROL_EXCEPTION_OWNER", ex.getErrorCode());
    }

    @Test
    void unknownScopeRejectedAndMissingExceptionIs404() {
        when(repo.findAllByTenant(tenant)).thenReturn(List.of());
        assertThrows(HandyFlowException.class, () -> service.list(tid, "nonsense", today));
        assertEquals("CONTROL_EXCEPTION_NOT_FOUND", assertThrows(HandyFlowException.class,
                () -> service.respond(tid, UUID.randomUUID(), alice, "Alice", "Some response text", today)).getErrorCode());
    }
}
