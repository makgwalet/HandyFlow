package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.security.domain.model.AccessPoint;
import za.co.handyflow.platform.security.domain.model.GateRegisterEntry;
import za.co.handyflow.platform.security.domain.repository.AccessPointRepository;
import za.co.handyflow.platform.security.domain.repository.GateRegisterEntryRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.LogArrivalRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A guard logs gate traffic only at the site they are on shift at, and always under their own name. */
class GateAccessServiceGuardTest {

    private static final TenantId TENANT = TenantId.generate();

    private final AccessPointRepository accessPoints = mock(AccessPointRepository.class);
    private final GateRegisterEntryRepository entries = mock(GateRegisterEntryRepository.class);
    private final GateAccessService service = new GateAccessService(accessPoints, entries, mock(GuardRepository.class),
            mock(DeviceSessionService.class), mock(EvidenceFacade.class));

    private final UUID guard = UUID.randomUUID();
    private final UUID mySite = UUID.randomUUID();

    private LogArrivalRequest arrivalAt(UUID accessPointId) {
        return new LogArrivalRequest(accessPointId, "someone-elses-device", "VISITOR", "Thabo", null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    @Test
    void aGateAtAnotherSiteLooksLikeItDoesNotExist() {
        UUID gateId = UUID.randomUUID();
        AccessPoint elsewhere = mock(AccessPoint.class);
        when(elsewhere.getSiteId()).thenReturn(UUID.randomUUID());
        when(accessPoints.findByIdAndTenant(gateId, TENANT)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.logArrivalByGuard(TENANT, guard, mySite, arrivalAt(gateId)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(entries, never()).save(any());
    }

    @Test
    void aVisitorAtAnotherSiteCannotBeLoggedOutByThisGuard() {
        UUID entryId = UUID.randomUUID();
        GateRegisterEntry other = mock(GateRegisterEntry.class);
        when(other.getSiteId()).thenReturn(UUID.randomUUID());
        when(entries.findByIdAndTenant(entryId, TENANT)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.logExitByGuard(TENANT, guard, mySite, entryId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aVisitorAlreadyDepartedIsAConflictNotAServerError() {
        UUID entryId = UUID.randomUUID();
        GateRegisterEntry gone = mock(GateRegisterEntry.class);
        when(gone.getSiteId()).thenReturn(mySite);
        when(gone.getStatus()).thenReturn("DEPARTED");
        when(entries.findByIdAndTenant(entryId, TENANT)).thenReturn(Optional.of(gone));

        assertThatThrownBy(() -> service.logExitByGuard(TENANT, guard, mySite, entryId))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already logged as departed");
        verify(entries, never()).save(any());
    }

    @Test
    void aPhotoOnAnotherSitesEntryIsNotFound() {
        UUID entryId = UUID.randomUUID();
        GateRegisterEntry other = mock(GateRegisterEntry.class);
        when(other.getSiteId()).thenReturn(UUID.randomUUID());
        when(entries.findByIdAndTenant(entryId, TENANT)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.attachEvidenceByGuard(TENANT, guard, mySite, entryId,
                new org.springframework.mock.web.MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[]{1}), "GENERAL_PHOTO"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aDepartureRecordedMoreThan48HoursAgoIsRefused() {
        UUID entryId = UUID.randomUUID();
        assertThatThrownBy(() -> service.logExitByGuard(TENANT, guard, mySite, entryId, java.time.Instant.now().minus(java.time.Duration.ofHours(49))))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("48 hours");
        verify(entries, never()).save(any());
    }
}
