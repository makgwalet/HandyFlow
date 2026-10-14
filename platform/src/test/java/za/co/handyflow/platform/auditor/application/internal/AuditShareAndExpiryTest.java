package za.co.handyflow.platform.auditor.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.auditor.domain.repository.AuditShareRepository;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditShareAndExpiryTest {

    AuditShareRepository repo = mock(AuditShareRepository.class);
    EvidenceFacade evidence = mock(EvidenceFacade.class);
    ControlExceptionFacade exceptions = mock(ControlExceptionFacade.class);
    AuditShareService service;
    TenantId tenant = TenantId.generate();
    UUID item = UUID.randomUUID(), user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AuditShareService(repo, evidence, exceptions);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void evidenceExists() {
        when(evidence.listByIds(tenant, List.of(item))).thenReturn(List.of(
                new EvidenceResponse(item, "f.pdf", "application/pdf", 1L, "OTHER", "ACTIVE", "U", Instant.now())));
    }

    @Test
    void sharesAnExistingItemWithAReason() {
        evidenceExists();
        when(repo.findLiveFor(tenant.getValue(), AuditShare.EVIDENCE, item)).thenReturn(Optional.empty());
        AuditShare s = service.share(tenant, AuditShare.EVIDENCE, item, "Needed for year-end", user, "Owner");
        assertTrue(s.isLive());
        assertEquals(tenant.getValue(), s.getTenantId());
    }

    @Test
    void refusesAnItemThatIsNotThisTenants() {
        when(evidence.listByIds(tenant, List.of(item))).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.share(tenant, AuditShare.EVIDENCE, item, "why", user, "Owner"));
        verify(repo, never()).save(any());
    }

    @Test
    void refusesAShareWithoutAReasonOrTwice() {
        evidenceExists();
        assertEquals("REASON_REQUIRED", assertThrows(HandyFlowException.class, () -> service.share(tenant, AuditShare.EVIDENCE, item, " ", user, "O")).getErrorCode());
        when(repo.findLiveFor(tenant.getValue(), AuditShare.EVIDENCE, item)).thenReturn(Optional.of(mock(AuditShare.class)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(HandyFlowException.class, () -> service.share(tenant, AuditShare.EVIDENCE, item, "why", user, "O")).getStatus());
    }

    @Test
    void findingsAreNotSharedHere() {
        assertEquals("USE_FINDING_SHARE", assertThrows(HandyFlowException.class, () -> service.share(tenant, AuditShare.FINDING, item, "why", user, "O")).getErrorCode());
    }

    @Test
    void withdrawingKeepsTheRecordAndNeedsAReason() {
        AuditShare s = AuditShare.share(tenant.getValue(), AuditShare.EVIDENCE, item, "why", user, "Owner");
        when(repo.findByTenantIdAndId(tenant.getValue(), s.getId())).thenReturn(Optional.of(s));
        assertThrows(HandyFlowException.class, () -> service.withdraw(tenant, s.getId(), " ", user, "Owner"));
        service.withdraw(tenant, s.getId(), "Wrong file", user, "Owner");
        assertFalse(s.isLive());
        assertEquals("Wrong file", s.getWithdrawnReason());
    }

    @Test
    void expiryMustBeFutureAndWithinTheLimit() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        assertNull(AuditorService.expiryFrom(null));
        assertEquals("EXPIRY_IN_PAST", assertThrows(HandyFlowException.class, () -> AuditorService.expiryFrom(today)).getErrorCode());
        assertEquals("EXPIRY_TOO_FAR", assertThrows(HandyFlowException.class, () -> AuditorService.expiryFrom(today.plusDays(400))).getErrorCode());
        Instant end = AuditorService.expiryFrom(today.plusDays(30));
        assertEquals(today.plusDays(31), end.atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate());
    }
}
