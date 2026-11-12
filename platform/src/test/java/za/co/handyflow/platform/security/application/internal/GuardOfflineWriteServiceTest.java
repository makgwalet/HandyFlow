package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.handyflow.platform.security.application.internal.GuardClientLedger.Kind;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.repository.AlarmEventRepository;
import za.co.handyflow.platform.security.dto.CreateIncidentRequest;
import za.co.handyflow.platform.security.dto.IncidentResponse;
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
    private final GuardOfflineWriteService service = new GuardOfflineWriteService(ledger, scans, incidents, controlRoom, alarms);

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
}
