package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.security.domain.model.Armoury;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.ArmouryLog;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Spring-free Mockito test of the new gates. Not run in the authoring sandbox (no Maven jars). */
class ArmouryServiceIssueTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final ArmouryRepository armouryRepo = mock(ArmouryRepository.class);
    private final ArmouryLogRepository logRepo = mock(ArmouryLogRepository.class);
    private final GuardRepository guardRepo = mock(GuardRepository.class);
    private final AuditEventRepository auditRepo = mock(AuditEventRepository.class);
    private final SecurityAlertNotifier alerts = mock(SecurityAlertNotifier.class);
    private final ArmouryService service = new ArmouryService(armouryRepo, logRepo, guardRepo, auditRepo);

    private Armoury firearm() {
        Armoury a = Armoury.register(tenant, "SN1", "Handgun", "Glock", "LIC1", null, LocalDate.now().plusYears(1), null);
        when(armouryRepo.findByTenantAndId(any(), any())).thenReturn(Optional.of(a));
        return a;
    }

    private Guard guard(LocalDate psiraExpiry) {
        Guard g = mock(Guard.class);
        when(g.isSchedulable()).thenReturn(true);
        when(g.hasFirearmCompetency()).thenReturn(true);
        when(g.getFullName()).thenReturn("Test Guard");
        when(g.getPsiraNumber()).thenReturn("PS1");
        when(g.getPsiraExpiryDate()).thenReturn(psiraExpiry);
        return g;
    }

    @Test
    void issueRefusedWhenPsiraExpired() {
        Armoury a = firearm();
        UUID guardId = UUID.randomUUID();
        when(guardRepo.findActiveById(any(), eq(guardId))).thenReturn(Optional.of(guard(LocalDate.now().minusDays(1))));
        HandyFlowException e = assertThrows(HandyFlowException.class, () ->
                service.issue(tenant, a.getId(), new IssueFirearmRequest(guardId, UUID.randomUUID(), null, null, null, null, null), UUID.randomUUID()));
        assertEquals("PSIRA_EXPIRED", e.getErrorCode());
        verify(logRepo, never()).save(any());
    }

    @Test
    void shiftRequiredWhenConfigured() {
        Armoury a = firearm();
        ReflectionTestUtils.setField(service, "requireShift", true);
        UUID guardId = UUID.randomUUID();
        when(guardRepo.findActiveById(any(), eq(guardId))).thenReturn(Optional.of(guard(LocalDate.now().plusYears(1))));
        HandyFlowException e = assertThrows(HandyFlowException.class, () ->
                service.issue(tenant, a.getId(), new IssueFirearmRequest(guardId, UUID.randomUUID(), null, null, null, null, null), UUID.randomUUID()));
        assertEquals("SHIFT_REQUIRED", e.getErrorCode());
    }

    @Test
    void reportLostStartsClockAndAlerts() {
        Armoury a = firearm();
        ReflectionTestUtils.setField(service, "alerts", alerts);
        ArmouryResponse r = service.reportLost(tenant, a.getId(), new ReportLostFirearmRequest("Stolen from vehicle", null), UUID.randomUUID());
        assertEquals("LOST", r.status());
        assertNotNull(r.sapsReportDueAt());
        assertFalse(r.sapsReportOverdue());
        verify(alerts).firearmLost(any(), eq(a.getId()), eq("SN1"), eq(false));
    }

    @Test
    void reportingLostTwiceRefused() {
        Armoury a = firearm();
        service.reportLost(tenant, a.getId(), new ReportLostFirearmRequest("x", null), UUID.randomUUID());
        HandyFlowException e = assertThrows(HandyFlowException.class, () ->
                service.reportLost(tenant, a.getId(), new ReportLostFirearmRequest("x", null), UUID.randomUUID()));
        assertEquals("ALREADY_LOST", e.getErrorCode());
    }

    @Test
    void sapsCaseOnlyForLostFirearm() {
        Armoury a = firearm();
        HandyFlowException e = assertThrows(HandyFlowException.class, () ->
                service.recordSapsCase(tenant, a.getId(), new RecordSapsCaseRequest("CAS 1"), UUID.randomUUID()));
        assertEquals("NOT_LOST", e.getErrorCode());
        service.reportLost(tenant, a.getId(), new ReportLostFirearmRequest("x", null), UUID.randomUUID());
        assertEquals("CAS 1", service.recordSapsCase(tenant, a.getId(), new RecordSapsCaseRequest(" CAS 1 "), UUID.randomUUID()).sapsCaseNumber());
    }

    @Test
    void returnRefusedWhenRoundsMissingOrShort() {
        Armoury a = firearm();
        UUID holder = UUID.randomUUID(), witness = UUID.randomUUID();
        a.markIssued(holder);
        ArmouryLog issueLog = ArmouryLog.record(tenant, a.getId(), holder, ArmouryLog.ArmouryAction.ISSUE, witness, null, null, null);
        issueLog.recordAmmo("9mm", 30, null);
        when(logRepo.findMostRecent(a.getId())).thenReturn(Optional.of(issueLog));
        Guard w = guard(LocalDate.now().plusYears(1));
        when(guardRepo.findActiveById(any(), eq(witness))).thenReturn(Optional.of(w));

        assertEquals("AMMO_COUNT_REQUIRED", assertThrows(HandyFlowException.class, () ->
                service.returnFirearm(tenant, a.getId(), new ReturnFirearmRequest(witness, null, null, null), UUID.randomUUID())).getErrorCode());
        assertEquals("AMMO_EXPLANATION_REQUIRED", assertThrows(HandyFlowException.class, () ->
                service.returnFirearm(tenant, a.getId(), new ReturnFirearmRequest(witness, null, 28, null), UUID.randomUUID())).getErrorCode());
        verify(logRepo, never()).save(any());
    }

    @Test
    void returnWithAllRoundsSucceeds() {
        Armoury a = firearm();
        UUID holder = UUID.randomUUID(), witness = UUID.randomUUID();
        a.markIssued(holder);
        ArmouryLog issueLog = ArmouryLog.record(tenant, a.getId(), holder, ArmouryLog.ArmouryAction.ISSUE, witness, null, null, null);
        issueLog.recordAmmo("9mm", 30, null);
        when(logRepo.findMostRecent(a.getId())).thenReturn(Optional.of(issueLog));
        when(guardRepo.findActiveById(any(), eq(witness))).thenReturn(Optional.of(guard(LocalDate.now().plusYears(1))));

        ArmouryResponse r = service.returnFirearm(tenant, a.getId(), new ReturnFirearmRequest(witness, null, 30, null), UUID.randomUUID());
        assertEquals("IN_ARMOURY", r.status());
        verify(logRepo).save(any());
    }
}
