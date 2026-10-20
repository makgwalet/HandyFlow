package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EarthFieldSyncServiceTest {
    EarthFieldSubmissionRepository submissions = mock(EarthFieldSubmissionRepository.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthMeterReadingRepository meterReadings = mock(EarthMeterReadingRepository.class);
    EarthAssetService assetService = mock(EarthAssetService.class);
    EarthTelematicsService telematics = mock(EarthTelematicsService.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthFieldSyncService service;
    TenantId tenant = TenantId.generate();
    UUID me = UUID.randomUUID(), boss = UUID.randomUUID();
    EarthAsset machine;
    Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        service = new EarthFieldSyncService(submissions, assets, meterReadings, assetService, telematics, trail, access);
        granted.add("EARTHMOVING_WRITE");
        machine = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", "Caterpillar", "D9T", 2020, "S1", "GP1",
                "OWN", null, null, null, new BigDecimal("18500"), new BigDecimal("2200"), null);
        machine.updateHours(new BigDecimal("1000"));
        when(assets.findActiveById(tenant, machine.getId())).thenReturn(Optional.of(machine));
        when(submissions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(submissions.findBySubmissionIds(any(), any())).thenReturn(List.of());
        when(meterReadings.findByAssetExcludingSource(any(), any(), anyString(), any())).thenReturn(List.of());
        when(submissions.findAppliedByAsset(any(), any(), anyString(), any())).thenReturn(List.of());
    }

    private FieldEntryRequest meter(UUID id, String value, Instant at) {
        return new FieldEntryRequest(id, "METER_READING", machine.getId(), at, new BigDecimal(value), null, null, null);
    }

    private FieldEntryResult one(FieldEntryRequest e) { return service.sync(tenant, new FieldSyncRequest(List.of(e)), me).get(0); }

    @Test void sendingNeedsWrite() {
        granted.clear();
        assertThrows(HandyFlowException.class, () -> service.sync(tenant, new FieldSyncRequest(List.of()), me));
    }

    @Test void aGoodMeterReadingIsReCheckedAndAppliedAsFieldWithTheSendersOwnPermissions() {
        FieldEntryResult r = one(meter(UUID.randomUUID(), "1010", now.minusSeconds(3600)));
        assertEquals("SYNCED", r.status());
        verify(assetService).updateHours(eq(tenant), eq(machine.getId()), any(), eq(me), eq("FIELD"));
    }

    @Test void theSameSubmissionSentTwiceIsHandledOnce() {
        UUID id = UUID.randomUUID();
        EarthFieldSubmission first = EarthFieldSubmission.received(tenant, id, "METER_READING", machine.getId(), now, new BigDecimal("1010"), null, null, null, me, "SYNCED", null);
        when(submissions.findBySubmissionIds(eq(tenant), any())).thenReturn(List.of(first));
        FieldEntryResult r = one(meter(id, "1010", now));
        assertTrue(r.duplicate());
        assertEquals("SYNCED", r.status());
        verify(assetService, never()).updateHours(any(), any(), any(), any(), anyString());
        verify(submissions, never()).save(any());
    }

    @Test void someoneElsesSubmissionIdIsNotFound() {
        UUID id = UUID.randomUUID();
        EarthFieldSubmission theirs = EarthFieldSubmission.received(tenant, id, "METER_READING", machine.getId(), now, new BigDecimal("1010"), null, null, null, boss, "SYNCED", null);
        when(submissions.findBySubmissionIds(eq(tenant), any())).thenReturn(List.of(theirs));
        assertThrows(ResourceNotFoundException.class, () -> one(meter(id, "1010", now)));
    }

    @Test void aReadingTakenBeforeANewerServerReadingIsKeptAsAConflictAndNeverApplied() {
        EarthMeterReading newer = mock(EarthMeterReading.class);
        when(newer.getRecordedAt()).thenReturn(now.minusSeconds(60));
        when(meterReadings.findByAssetExcludingSource(eq(tenant), eq(machine.getId()), eq("FIELD"), any())).thenReturn(List.of(newer));
        FieldEntryResult r = one(meter(UUID.randomUUID(), "1005", now.minusSeconds(7200)));
        assertEquals("CONFLICT", r.status());
        verify(assetService, never()).updateHours(any(), any(), any(), any(), anyString());
        verify(submissions).save(any());   // kept
    }

    @Test void aLateEntryIsJudgedByCaptureTimeOfEarlierFieldEntriesNotTheirSyncTime() {
        EarthFieldSubmission earlier = EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", machine.getId(), now.minusSeconds(7200),
                new BigDecimal("1005"), null, null, null, me, "SYNCED", null);
        when(submissions.findAppliedByAsset(eq(tenant), eq(machine.getId()), eq("METER_READING"), any())).thenReturn(List.of(earlier));
        assertEquals("SYNCED", one(meter(UUID.randomUUID(), "1010", now.minusSeconds(3600))).status());
        assertEquals("CONFLICT", one(meter(UUID.randomUUID(), "1002", now.minusSeconds(9000))).status());
    }

    @Test void aLowerReadingIsRejectedWithAReason() {
        FieldEntryResult r = one(meter(UUID.randomUUID(), "900", now));
        assertEquals("REJECTED", r.status());
        assertTrue(r.reason().contains("correction"));
        verify(assetService, never()).updateHours(any(), any(), any(), any(), anyString());
    }

    @Test void deploymentReleaseAndDisposalApprovalAreRefusedOffline() {
        for (String k : new String[]{"DEPLOYMENT", "MAINTENANCE_RELEASE", "DISPOSAL_APPROVAL"}) {
            FieldEntryResult r = one(new FieldEntryRequest(UUID.randomUUID(), k, machine.getId(), now, null, null, null, null));
            assertEquals("REJECTED", r.status(), k);
            assertTrue(r.reason().contains("fresh check"), k);
        }
    }

    @Test void aMissingOrRetiredMachineIsRejected() {
        assertEquals("REJECTED", one(new FieldEntryRequest(UUID.randomUUID(), "METER_READING", UUID.randomUUID(), now, BigDecimal.TEN, null, null, null)).status());
        machine.retire();
        FieldEntryResult r = one(meter(UUID.randomUUID(), "1010", now));
        assertEquals("REJECTED", r.status());
        assertTrue(r.reason().contains("retired"));
    }

    @Test void aPositionIsRecordedAtTheTimeItWasCaptured() {
        Instant at = now.minus(Duration.ofHours(5));
        FieldEntryResult r = one(new FieldEntryRequest(UUID.randomUUID(), "POSITION_READING", machine.getId(), at, null, new BigDecimal("-26.2"), new BigDecimal("28.0"), "gate"));
        assertEquals("SYNCED", r.status());
        verify(telematics).recordManual(eq(tenant), eq(machine.getId()), argThat(q -> at.equals(q.recordedAt()) && q.meterValue() == null), eq(me));
    }

    @Test void aPositionOlderThanThirtyDaysIsRejectedNotThrown() {
        FieldEntryResult r = one(new FieldEntryRequest(UUID.randomUUID(), "POSITION_READING", machine.getId(), now.minus(Duration.ofDays(40)), null, BigDecimal.ONE, BigDecimal.ONE, null));
        assertEquals("REJECTED", r.status());
        verify(telematics, never()).recordManual(any(), any(), any(), any());
    }

    @Test void oneBadEntryDoesNotStopTheOthersAndEntriesAreHandledInCaptureOrder() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        List<FieldEntryResult> out = service.sync(tenant, new FieldSyncRequest(List.of(
                meter(c, "1020", now.minusSeconds(100)), meter(a, "900", now.minusSeconds(3000)), meter(b, "1010", now.minusSeconds(2000)))), me);
        assertEquals(List.of(a, b, c), out.stream().map(FieldEntryResult::submissionId).toList());
        assertEquals(List.of("REJECTED", "SYNCED", "SYNCED"), out.stream().map(FieldEntryResult::status).toList());
    }

    @Test void aHugeBatchIsRefused() {
        List<FieldEntryRequest> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) many.add(meter(UUID.randomUUID(), "1010", now));
        assertThrows(IllegalArgumentException.class, () -> service.sync(tenant, new FieldSyncRequest(many), me));
    }

    @Test void anEntryWithNoSubmissionIdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.sync(tenant, new FieldSyncRequest(List.of(meter(null, "1010", now))), me));
    }

    @Test void reviewingNeedsMaintainAndApplyingChecksTheMachineAgain() {
        EarthFieldSubmission c = EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", machine.getId(), now.minusSeconds(7200),
                new BigDecimal("1005"), null, null, null, me, "CONFLICT", "Newer reading exists");
        when(submissions.findByTenantAndId(tenant, c.getId())).thenReturn(Optional.of(c));
        assertThrows(HandyFlowException.class, () -> service.review(tenant, c.getId(), new ReviewFieldConflictRequest("APPLIED", null), boss));
        granted.add("EARTHMOVING_MAINTAIN");
        machine.updateHours(new BigDecimal("1100"));   // the machine has moved on past the entry
        assertThrows(IllegalStateException.class, () -> service.review(tenant, c.getId(), new ReviewFieldConflictRequest("APPLIED", null), boss));
        assertTrue(c.isOpenConflict());
        verify(assetService, never()).updateHours(any(), any(), any(), any(), anyString());
    }

    @Test void keepingTheServerVersionAppliesNothingAndClosesTheConflict() {
        EarthFieldSubmission c = EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", machine.getId(), now.minusSeconds(7200),
                new BigDecimal("1005"), null, null, null, me, "CONFLICT", "Newer reading exists");
        when(submissions.findByTenantAndId(tenant, c.getId())).thenReturn(Optional.of(c));
        granted.add("EARTHMOVING_MAINTAIN");
        FieldEntryResult r = service.review(tenant, c.getId(), new ReviewFieldConflictRequest("KEPT_SERVER", "Server was right"), boss);
        assertEquals("KEPT_SERVER", r.reviewDecision());
        verify(assetService, never()).updateHours(any(), any(), any(), any(), anyString());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_FIELD_CONFLICT_REVIEWED"), anyString(), eq(c.getId()), any(), any());
    }

    @Test void applyingAConflictAppliesItAsAFieldReading() {
        EarthFieldSubmission c = EarthFieldSubmission.received(tenant, UUID.randomUUID(), "METER_READING", machine.getId(), now.minusSeconds(7200),
                new BigDecimal("1005"), null, null, null, me, "CONFLICT", "Newer reading exists");
        when(submissions.findByTenantAndId(tenant, c.getId())).thenReturn(Optional.of(c));
        granted.add("EARTHMOVING_MAINTAIN");
        service.review(tenant, c.getId(), new ReviewFieldConflictRequest("APPLIED", null), boss);
        verify(assetService).updateHours(eq(tenant), eq(machine.getId()), any(), eq(boss), eq("FIELD"));
    }
}
