package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.security.domain.model.DeviceSession;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.ResourceCustody;
import za.co.handyflow.platform.security.domain.model.SecurityDevice;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.DeviceSessionRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.ResourceCustodyRepository;
import za.co.handyflow.platform.security.domain.repository.SecurityDeviceRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardOpenSessionRequest;
import za.co.handyflow.platform.security.dto.CheckoutResourceRequest;
import za.co.handyflow.platform.security.dto.ReturnResourceRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guard-route rules: identity from the token, server-decided checks, own session only. */
class DeviceSessionServiceGuardTest {

    private static final TenantId TENANT = TenantId.generate();

    private final SecurityDeviceRepository devices = mock(SecurityDeviceRepository.class);
    private final DeviceSessionRepository sessions = mock(DeviceSessionRepository.class);
    private final ResourceCustodyRepository custody = mock(ResourceCustodyRepository.class);
    private final GuardRepository guards = mock(GuardRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final SiteRepository sites = mock(SiteRepository.class);
    private final DeviceSessionService service = new DeviceSessionService(
            devices, sessions, custody, guards, shifts, mock(PatrolRoundService.class), sites);

    private final UUID me = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private void deviceAtSiteWithPosition(String lat, String lon) {
        SecurityDevice device = mock(SecurityDevice.class);
        when(device.getId()).thenReturn(UUID.randomUUID());
        when(device.isActive()).thenReturn(true);
        when(device.getSiteId()).thenReturn(siteId);
        when(devices.findByTenantIdAndDeviceHardwareId(TENANT, "hw-1")).thenReturn(Optional.of(device));
        when(sessions.findOpenByDevice(any())).thenReturn(Optional.empty());
        when(sessions.findOpenByGuard(me)).thenReturn(Optional.empty());
        Guard guard = mock(Guard.class);
        when(guard.isSchedulable()).thenReturn(true);
        when(guard.getFullName()).thenReturn("Lerato Mokoena");
        when(guards.findActiveById(TENANT, me)).thenReturn(Optional.of(guard));
        when(shifts.findScheduledStartingBefore(any(), any())).thenReturn(List.of());
        Site site = mock(Site.class);
        when(site.getLatitude()).thenReturn(lat == null ? null : new BigDecimal(lat));
        when(site.getLongitude()).thenReturn(lon == null ? null : new BigDecimal(lon));
        when(sites.findActiveById(TENANT, siteId)).thenReturn(Optional.of(site));
    }

    private DeviceSession openAndCapture(GuardOpenSessionRequest req) {
        service.openGuardSession(TENANT, me, req);
        ArgumentCaptor<DeviceSession> saved = ArgumentCaptor.forClass(DeviceSession.class);
        verify(sessions).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void theSessionBelongsToTheTokenGuardAndThePinFlagIsSetByTheServer() {
        deviceAtSiteWithPosition("-26.2041", "28.0473");
        DeviceSession s = openAndCapture(new GuardOpenSessionRequest(
                "hw-1", new BigDecimal("-26.2041"), new BigDecimal("28.0473"), 10.0));
        assertThat(s.getGuardId()).isEqualTo(me);
        assertThat(s.isStartPinVerified()).isTrue();
        assertThat(s.getStartFaceMatchConfidence()).isNull();
    }

    @Test
    void onSiteIsRecordedAsPassedAndFarAwayAsFailed() {
        deviceAtSiteWithPosition("-26.2041", "28.0473");
        assertThat(openAndCapture(new GuardOpenSessionRequest(
                "hw-1", new BigDecimal("-26.2041"), new BigDecimal("28.0473"), 10.0)).getStartGeofenceOk()).isTrue();
    }

    @Test
    void clockingInFromFarAwayIsRecordedAsOutsideTheGeofence() {
        deviceAtSiteWithPosition("-26.2041", "28.0473");
        assertThat(openAndCapture(new GuardOpenSessionRequest(
                "hw-1", new BigDecimal("-25.7479"), new BigDecimal("28.2293"), 10.0)).getStartGeofenceOk()).isFalse();
    }

    @Test
    void noPositionFromThePhoneIsUnknownNotPassed() {
        deviceAtSiteWithPosition("-26.2041", "28.0473");
        assertThat(openAndCapture(new GuardOpenSessionRequest("hw-1", null, null, null)).getStartGeofenceOk()).isNull();
    }

    @Test
    void aSiteWithNoSavedPositionIsUnknown() {
        deviceAtSiteWithPosition(null, null);
        assertThat(openAndCapture(new GuardOpenSessionRequest(
                "hw-1", new BigDecimal("-26.2041"), new BigDecimal("28.0473"), 10.0)).getStartGeofenceOk()).isNull();
    }

    @Test
    void aGuardCannotUseAnotherGuardsSession() {
        UUID sessionId = UUID.randomUUID();
        DeviceSession theirs = mock(DeviceSession.class);
        when(theirs.getTenantId()).thenReturn(TENANT);
        when(theirs.getGuardId()).thenReturn(someoneElse);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(theirs));
        assertThatThrownBy(() -> service.assertSessionOwnedBy(TENANT, sessionId, me))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aGuardCanUseTheirOwnSession() {
        UUID sessionId = UUID.randomUUID();
        DeviceSession mine = mock(DeviceSession.class);
        when(mine.getTenantId()).thenReturn(TENANT);
        when(mine.getGuardId()).thenReturn(me);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(mine));
        assertThatCode(() -> service.assertSessionOwnedBy(TENANT, sessionId, me)).doesNotThrowAnyException();
    }

    @Test
    void aSessionFromAnotherTenantLooksLikeItDoesNotExist() {
        UUID sessionId = UUID.randomUUID();
        DeviceSession other = mock(DeviceSession.class);
        when(other.getTenantId()).thenReturn(TenantId.generate());
        when(other.getGuardId()).thenReturn(me);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.assertSessionOwnedBy(TENANT, sessionId, me))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aGuardCannotReturnEquipmentCheckedOutByAnotherGuard() {
        UUID custodyId = UUID.randomUUID();
        ResourceCustody theirs = mock(ResourceCustody.class);
        when(theirs.getTenantId()).thenReturn(TENANT);
        when(theirs.getGuardId()).thenReturn(someoneElse);
        when(custody.findById(custodyId)).thenReturn(Optional.of(theirs));
        assertThatThrownBy(() -> service.assertCustodyOwnedBy(TENANT, custodyId, me))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anUnknownResourceTypeIsABadRequestNotAServerError() {
        UUID sessionId = UUID.randomUUID();
        DeviceSession open = mock(DeviceSession.class);
        when(open.getTenantId()).thenReturn(TENANT);
        when(open.isOpen()).thenReturn(true);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.checkoutResource(TENANT, sessionId, new CheckoutResourceRequest("JETPACK", "J-1", null, null)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Unknown resource type");
    }

    @Test
    void anItemAlreadyReturnedCannotBeReturnedAgain() {
        UUID custodyId = UUID.randomUUID();
        ResourceCustody item = mock(ResourceCustody.class);
        when(item.getTenantId()).thenReturn(TENANT);
        when(item.isCheckedOut()).thenReturn(false);
        when(custody.findById(custodyId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.returnResource(TENANT, custodyId, new ReturnResourceRequest("GOOD", null)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already been returned");
        verify(custody, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void anUnknownConditionIsABadRequest() {
        UUID custodyId = UUID.randomUUID();
        ResourceCustody item = mock(ResourceCustody.class);
        when(item.getTenantId()).thenReturn(TENANT);
        when(custody.findById(custodyId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.returnResource(TENANT, custodyId, new ReturnResourceRequest("SHINY", null)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("GOOD, DAMAGED or MISSING");
    }

    @Test
    void myOpenItemsAreOnlyMyOwnOldestFirst() {
        ResourceCustody older = mock(ResourceCustody.class);
        ResourceCustody newer = mock(ResourceCustody.class);
        when(older.getCheckedOutAt()).thenReturn(java.time.Instant.parse("2026-11-12T06:00:00Z"));
        when(newer.getCheckedOutAt()).thenReturn(java.time.Instant.parse("2026-11-12T07:00:00Z"));
        when(custody.findCheckedOutByGuard(me)).thenReturn(List.of(newer, older));

        assertThat(service.openCustodyOf(me)).containsExactly(older, newer);
        verify(custody, org.mockito.Mockito.never()).findCheckedOutByGuard(someoneElse);
    }

    @Test
    void aGuardsOwnPhoneWithNoSiteStillStartsTheirShiftAndIsCheckedAgainstTheShiftsSite() {
        deviceAtSiteWithPosition("-26.2041", "28.0473");
        SecurityDevice personal = mock(SecurityDevice.class);
        when(personal.getId()).thenReturn(UUID.randomUUID());
        when(personal.isActive()).thenReturn(true);
        when(personal.getSiteId()).thenReturn(null);
        when(devices.findByTenantIdAndDeviceHardwareId(TENANT, "hw-1")).thenReturn(Optional.of(personal));
        za.co.handyflow.platform.security.domain.model.Shift mine = mock(za.co.handyflow.platform.security.domain.model.Shift.class);
        when(mine.getId()).thenReturn(UUID.randomUUID());
        when(mine.getGuardId()).thenReturn(me);
        when(mine.getSiteId()).thenReturn(siteId);
        when(mine.getStartAt()).thenReturn(java.time.Instant.now().minusSeconds(300));
        when(shifts.findScheduledStartingBefore(any(), any())).thenReturn(List.of(mine));

        DeviceSession s = openAndCapture(new GuardOpenSessionRequest(
                "hw-1", new BigDecimal("-26.2041"), new BigDecimal("28.0473"), 10.0));
        assertThat(s.getShiftId()).isEqualTo(mine.getId());
        assertThat(s.getStartGeofenceOk()).isTrue();
    }
}
