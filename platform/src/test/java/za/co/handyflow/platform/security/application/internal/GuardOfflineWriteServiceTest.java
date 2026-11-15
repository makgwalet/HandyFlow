package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.application.internal.GuardClientLedger.Kind;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.repository.AlarmEventRepository;
import za.co.handyflow.platform.security.dto.CreateIncidentRequest;
import za.co.handyflow.platform.security.dto.GateRegisterEntryResponse;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.security.dto.LogArrivalRequest;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.security.dto.TriggerDuressRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A retried scan, incident report or panic press is answered from the first one and creates nothing new. */
class GuardOfflineWriteServiceTest {

    private static final TenantId TENANT = TenantId.generate();

    private final GuardClientLedger ledger = mock(GuardClientLedger.class);
    private final CheckpointScanService scans = mock(CheckpointScanService.class);
    private final IncidentService incidents = mock(IncidentService.class);
    private final ControlRoomService controlRoom = mock(ControlRoomService.class);
    private final AlarmEventRepository alarms = mock(AlarmEventRepository.class);
    private final IncidentCaseService cases = mock(IncidentCaseService.class);
    private final EvidenceFacade evidenceFacade = mock(EvidenceFacade.class);
    private final GateAccessService gates = mock(GateAccessService.class);
    private final GuardOfflineWriteService service = new GuardOfflineWriteService(ledger, scans, incidents, controlRoom, alarms, cases, evidenceFacade, gates);

    private final UUID guard = UUID.randomUUID();
    private final UUID ref = UUID.randomUUID();

    private ScanRequest scanReq(UUID clientRef) {
        return new ScanRequest("qr", null, null, null, null, null, null, null, "QR", "dev", clientRef, null);
    }

    @Test
    void aScanWithNoClientIdBehavesAsBefore() {
        ScanResponse made = new ScanResponse(UUID.randomUUID(), "Gate", "Site", Instant.now());
        when(scans.scan(any(), any(), any())).thenReturn(made);
        assertThat(service.scan(TENANT, guard, scanReq(null))).isSameAs(made);
        verify(ledger, never()).find(any(), any(), any(), any());
        verify(ledger, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void theFirstScanWithAClientIdIsDoneAndRemembered() {
        ScanResponse made = new ScanResponse(UUID.randomUUID(), "Gate", "Site", Instant.now());
        when(ledger.find(TENANT, Kind.SCAN, ref, guard)).thenReturn(Optional.empty());
        when(scans.scan(any(), any(), any())).thenReturn(made);

        assertThat(service.scan(TENANT, guard, scanReq(ref))).isSameAs(made);
        verify(ledger).record(TENANT, guard, Kind.SCAN, ref, made.logId());
    }

    @Test
    void aRetriedScanReturnsTheFirstResultAndSavesNothing() {
        UUID firstLog = UUID.randomUUID();
        ScanResponse first = new ScanResponse(firstLog, "Gate", "Site", Instant.now());
        when(ledger.find(TENANT, Kind.SCAN, ref, guard)).thenReturn(Optional.of(firstLog));
        when(scans.describe(TENANT, firstLog)).thenReturn(first);

        assertThat(service.scan(TENANT, guard, scanReq(ref))).isSameAs(first);
        verify(scans, never()).scan(any(), any(), any());
        verify(ledger, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void aRetriedIncidentReportIsNotFiledTwice() {
        UUID firstId = UUID.randomUUID();
        IncidentResponse first = mock(IncidentResponse.class);
        when(ledger.find(TENANT, Kind.INCIDENT, ref, guard)).thenReturn(Optional.of(firstId));
        when(incidents.getIncidentDetail(TENANT, firstId)).thenReturn(first);

        CreateIncidentRequest req = new CreateIncidentRequest(UUID.randomUUID(), null, guard, "Gate forced", null,
                "LOW", null, null, null, ref, null);
        assertThat(service.incident(TENANT, guard, "Lerato", req)).isSameAs(first);
        verify(incidents, never()).createIncident(any(), any(), any(), any());
    }

    @Test
    void aRetriedPanicPressIsNotASecondAlarm() {
        UUID firstId = UUID.randomUUID();
        AlarmEvent first = mock(AlarmEvent.class);
        when(ledger.find(TENANT, Kind.DURESS, ref, guard)).thenReturn(Optional.of(firstId));
        when(alarms.findById(firstId)).thenReturn(Optional.of(first));

        TriggerDuressRequest req = new TriggerDuressRequest(null, guard, null, null, ref, null);
        assertThat(service.duress(TENANT, guard, req)).isSameAs(first);
        verify(controlRoom, never()).triggerDuress(any(), any());
    }

    @Test
    void twoRetriesRacingEachOtherLeaveOneWinnerAndTheLoserIsToldToRetry() {
        ScanResponse made = new ScanResponse(UUID.randomUUID(), "Gate", "Site", Instant.now());
        when(ledger.find(TENANT, Kind.SCAN, ref, guard)).thenReturn(Optional.empty());
        when(scans.scan(any(), any(), any())).thenReturn(made);
        doThrow(new DataIntegrityViolationException("duplicate")).when(ledger).record(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.scan(TENANT, guard, scanReq(ref)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already being processed");
    }

    private static EvidenceResponse evidence(UUID id) {
        return new EvidenceResponse(id, "p.jpg", "image/jpeg", 3, "PHOTO", "ACTIVE", "Lerato", Instant.now());
    }

    private final MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @Test
    void aPhotoSentBeforeItsIncidentHasArrivedIsToldToRetry() {
        when(ledger.find(TENANT, Kind.INCIDENT, ref, guard)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evidence(TENANT, guard, "Lerato", ref, UUID.randomUUID(), photo, null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not reached the server");
        verify(cases, never()).attachByGuard(any(), any(), any(), any(), any(), any());
    }

    @Test
    void theFirstUploadOfAPhotoIsAttachedAndRemembered() {
        UUID incidentId = UUID.randomUUID();
        UUID photoRef = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        EvidenceResponse made = evidence(evidenceId);
        when(ledger.find(TENANT, Kind.INCIDENT, ref, guard)).thenReturn(Optional.of(incidentId));
        when(ledger.find(TENANT, Kind.EVIDENCE, photoRef, guard)).thenReturn(Optional.empty());
        when(cases.attachByGuard(TENANT, incidentId, guard, photo, "Gate", "Lerato")).thenReturn(made);

        assertThat(service.evidence(TENANT, guard, "Lerato", ref, photoRef, photo, "Gate")).isSameAs(made);
        verify(ledger).record(TENANT, guard, Kind.EVIDENCE, photoRef, evidenceId);
    }

    @Test
    void aRetriedPhotoIsNotAttachedTwice() {
        UUID incidentId = UUID.randomUUID();
        UUID photoRef = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        EvidenceResponse first = evidence(evidenceId);
        when(ledger.find(TENANT, Kind.INCIDENT, ref, guard)).thenReturn(Optional.of(incidentId));
        when(ledger.find(TENANT, Kind.EVIDENCE, photoRef, guard)).thenReturn(Optional.of(evidenceId));
        when(evidenceFacade.listFor(TENANT, "security", "Incident", incidentId)).thenReturn(java.util.List.of(first));

        assertThat(service.evidence(TENANT, guard, "Lerato", ref, photoRef, photo, null)).isSameAs(first);
        verify(cases, never()).attachByGuard(any(), any(), any(), any(), any(), any());
    }

    private LogArrivalRequest arrival(UUID clientRef) {
        return new LogArrivalRequest(UUID.randomUUID(), "dev", "VISITOR", "Thabo", null, null, null, null, null, null,
                null, null, null, null, clientRef, null);
    }

    @Test
    void aRetriedGateArrivalDoesNotLogTheVisitorTwice() {
        UUID siteId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        GateRegisterEntryResponse first = mock(GateRegisterEntryResponse.class);
        when(ledger.find(TENANT, Kind.GATE_ENTRY, ref, guard)).thenReturn(Optional.of(firstId));
        when(gates.getEntry(TENANT, firstId)).thenReturn(first);

        assertThat(service.gateArrival(TENANT, guard, siteId, arrival(ref))).isSameAs(first);
        verify(gates, never()).logArrivalByGuard(any(), any(), any(), any());
    }

    @Test
    void theFirstGateArrivalIsLoggedAndRemembered() {
        UUID siteId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        GateRegisterEntryResponse made = mock(GateRegisterEntryResponse.class);
        when(made.id()).thenReturn(entryId);
        LogArrivalRequest req = arrival(ref);
        when(ledger.find(TENANT, Kind.GATE_ENTRY, ref, guard)).thenReturn(Optional.empty());
        when(gates.logArrivalByGuard(TENANT, guard, siteId, req)).thenReturn(made);

        assertThat(service.gateArrival(TENANT, guard, siteId, req)).isSameAs(made);
        verify(ledger).record(TENANT, guard, Kind.GATE_ENTRY, ref, entryId);
    }

    private static final za.co.handyflow.platform.security.dto.LogExitRequest EXIT =
            new za.co.handyflow.platform.security.dto.LogExitRequest("dev", Instant.parse("2026-11-12T10:00:00Z"));

    @Test
    void aDepartureForAnArrivalThatHasNotReachedTheServerIsToldToRetry() {
        when(ledger.find(TENANT, Kind.GATE_ENTRY, ref, guard)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.gateExitByRef(TENANT, guard, UUID.randomUUID(), ref, EXIT))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not reached the server");
        verify(gates, never()).logExitByGuard(any(), any(), any(), any(), any());
    }

    @Test
    void aDepartureByRefClosesTheEntryTheArrivalMadeWithThePhonesTime() {
        UUID siteId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(ledger.find(TENANT, Kind.GATE_ENTRY, ref, guard)).thenReturn(Optional.of(entryId));

        service.gateExitByRef(TENANT, guard, siteId, ref, EXIT);

        verify(gates).logExitByGuard(TENANT, guard, siteId, entryId, EXIT.recordedAt());
    }

    @Test
    void aRetriedGatePhotoIsNotAttachedTwice() {
        UUID entryId = UUID.randomUUID();
        UUID photoRef = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        EvidenceResponse first = evidence(evidenceId);
        when(ledger.find(TENANT, Kind.EVIDENCE, photoRef, guard)).thenReturn(Optional.of(evidenceId));
        when(evidenceFacade.listFor(TENANT, "security", "GateRegisterEntry", entryId)).thenReturn(java.util.List.of(first));

        assertThat(service.gateEvidence(TENANT, guard, UUID.randomUUID(), entryId, photoRef, photo, "ID_DOCUMENT")).isSameAs(first);
        verify(gates, never()).attachEvidenceByGuard(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aGatePhotoForAnArrivalNotYetSentIsToldToRetry() {
        when(ledger.find(TENANT, Kind.GATE_ENTRY, ref, guard)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.gateEvidenceByRef(TENANT, guard, UUID.randomUUID(), ref, UUID.randomUUID(), photo, "ID_DOCUMENT"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not reached the server");
    }
}
