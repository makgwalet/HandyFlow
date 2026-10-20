package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import za.co.handyflow.platform.fleet.domain.model.*;
import za.co.handyflow.platform.fleet.domain.repository.*;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccidentServiceTest {

    AccidentRepository accidents = mock(AccidentRepository.class);
    AccidentEvidenceRepository evidence = mock(AccidentEvidenceRepository.class);
    VehicleRepository vehicles = mock(VehicleRepository.class);
    DriverRepository drivers = mock(DriverRepository.class);
    VehicleInsuranceRepository insurance = mock(VehicleInsuranceRepository.class);
    AccidentReferenceGenerator references = mock(AccidentReferenceGenerator.class);
    FleetUploadSupport uploads = mock(FleetUploadSupport.class);
    NotificationService notifications = mock(NotificationService.class);
    TenantAdminRecipients admins = mock(TenantAdminRecipients.class);

    AccidentService service;
    TenantId tenant = TenantId.generate();
    UUID user = UUID.randomUUID();
    Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new AccidentService(accidents, evidence, vehicles, drivers, insurance, references, uploads,
                notifications, admins);
        vehicle = Vehicle.create(tenant, "CA 123-456", "Toyota", "Hilux", 2022, "White", null, "BAKKIE", "DIESEL",
                null, null, null, null, null, null, null, null, null);
        when(vehicles.findActiveById(eq(tenant), any())).thenReturn(Optional.empty());
        when(vehicles.findActiveById(tenant, vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(references.next(tenant)).thenReturn("ACC-2026-00001");
        when(insurance.findByTenantIdAndVehicleId(eq(tenant), any())).thenReturn(Optional.empty());
        when(admins.resolveTenantAdmins(tenant)).thenReturn(List.of(Recipient.external("Admin", "a@x.co.za", null)));
        when(evidence.countActiveByAccident(eq(tenant), any())).thenReturn(0L);
    }

    AccidentDetailsRequest details(Instant at, Integer injuries, Integer deaths, UUID driverId) {
        return new AccidentDetailsRequest(driverId, "  Sipho  ", null, at, " N1 near Paarl ", WeatherCondition.RAIN,
                RoadCondition.WET, null, null, null, null, null, null, null, "  Rear-ended at a robot  ", null,
                injuries, deaths, new BigDecimal("12000.00"), null, null);
    }

    NotificationRequest sentNotification() {
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        return c.getValue();
    }

    @Test void reportingAnAccidentIssuesAReferenceSavesItAndWarnsTheAdmins() {
        Instant at = Instant.now().minus(1, ChronoUnit.HOURS);
        AccidentResponse r = service.report(tenant, user, new ReportAccidentRequest(vehicle.getId(), details(at, null, null, null)));

        assertEquals("ACC-2026-00001", r.referenceNumber());
        assertEquals("REPORTED", r.status());
        assertEquals("NOT_CLAIMED", r.claimStatus());
        assertEquals("Rear-ended at a robot", r.description());
        assertEquals("N1 near Paarl", r.location());
        assertEquals("Sipho", r.driverName());
        assertEquals(0, r.injuriesCount());
        assertTrue(r.vehicleDrivable());
        verify(accidents).save(any(Accident.class));

        NotificationRequest n = sentNotification();
        assertEquals(NotificationType.ACCIDENT_REPORTED, n.type());
        assertEquals(NotificationSeverity.WARNING, n.severity());
        assertFalse(n.channels().contains(NotificationChannel.SMS));
    }

    @Test void anAccidentWithInjuriesIsCriticalAndGoesOutBySms() {
        service.report(tenant, user, new ReportAccidentRequest(vehicle.getId(),
                details(Instant.now().minus(1, ChronoUnit.HOURS), 2, 0, null)));
        NotificationRequest n = sentNotification();
        assertEquals(NotificationSeverity.CRITICAL, n.severity());
        assertEquals(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS), n.channels());
        assertTrue(n.message().contains("Injuries: 2"));
    }

    @Test void aFatalityIsCriticalToo() {
        service.report(tenant, user, new ReportAccidentRequest(vehicle.getId(),
                details(Instant.now().minus(1, ChronoUnit.HOURS), 0, 1, null)));
        assertEquals(NotificationSeverity.CRITICAL, sentNotification().severity());
    }

    @Test void theClaimInsurerAndExcessAreFilledInFromTheVehiclesPolicy() {
        VehicleInsurance policy = VehicleInsurance.create(tenant, vehicle.getId());
        policy.apply(true, "Santam", "POL-1", InsurancePolicyType.COMPREHENSIVE, LocalDate.now().minusMonths(2),
                LocalDate.now().plusMonths(10), null, new BigDecimal("5000.00"), null, null, null, null,
                false, null, null, false, null, null, user);
        when(insurance.findByTenantIdAndVehicleId(tenant, vehicle.getId())).thenReturn(Optional.of(policy));

        AccidentResponse r = service.report(tenant, user, new ReportAccidentRequest(vehicle.getId(),
                details(Instant.now().minus(1, ChronoUnit.HOURS), null, null, null)));
        assertEquals("Santam", r.claimInsurer());
        assertEquals(0, new BigDecimal("5000.00").compareTo(r.claimExcess()));
        assertEquals("NOT_CLAIMED", r.claimStatus());
    }

    @Test void aLapsedOrUninsuredPolicyPrefillsNothing() {
        VehicleInsurance policy = VehicleInsurance.create(tenant, vehicle.getId());
        policy.apply(false, "Santam", null, null, null, null, null, new BigDecimal("5000.00"), null, null, null, null,
                false, null, null, false, null, null, user);
        when(insurance.findByTenantIdAndVehicleId(tenant, vehicle.getId())).thenReturn(Optional.of(policy));
        AccidentResponse r = service.report(tenant, user, new ReportAccidentRequest(vehicle.getId(),
                details(Instant.now().minus(1, ChronoUnit.HOURS), null, null, null)));
        assertNull(r.claimInsurer());
    }

    @Test void aVehicleOfAnotherTenantIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.report(tenant, user,
                new ReportAccidentRequest(UUID.randomUUID(), details(Instant.now().minusSeconds(60), null, null, null))));
        verify(accidents, never()).save(any());
        verify(notifications, never()).send(any());
    }

    @Test void anAccidentCannotBeInTheFuture() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.report(tenant, user,
                new ReportAccidentRequest(vehicle.getId(), details(Instant.now().plus(1, ChronoUnit.DAYS), null, null, null))));
        assertTrue(e.getMessage().contains("future"));
        verify(accidents, never()).save(any());
    }

    @Test void aDriverFromTheRegisterFillsInTheName() {
        Driver d = Driver.create(tenant, "Thabo", "Mokoena", null, null, null, "LIC1", "EC", LocalDate.now().plusYears(2),
                false, null, null, null, null);
        when(drivers.findActiveById(tenant, d.getId())).thenReturn(Optional.of(d));
        AccidentResponse r = service.report(tenant, user, new ReportAccidentRequest(vehicle.getId(),
                details(Instant.now().minusSeconds(3600), null, null, d.getId())));
        assertEquals("Thabo Mokoena", r.driverName());
        assertEquals(d.getId(), r.driverId());
    }

    @Test void anUnknownDriverIsRefused() {
        UUID id = UUID.randomUUID();
        when(drivers.findActiveById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.report(tenant, user,
                new ReportAccidentRequest(vehicle.getId(), details(Instant.now().minusSeconds(3600), null, null, id))));
    }

    // ── Case rules ───────────────────────────────────────────────────────

    Accident existing() {
        Accident a = Accident.report(tenant, "ACC-2026-00009", vehicle.getId(),
                service.toDetails(tenant, details(Instant.now().minus(2, ChronoUnit.DAYS), null, null, null)), user);
        when(accidents.findByTenantAndId(tenant, a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test void aWriteOffCannotBeDeclaredFromTheStatusEndpoint() {
        Accident a = existing();
        assertThrows(IllegalArgumentException.class, () ->
                service.changeStatus(tenant, a.getId(), new ChangeAccidentStatusRequest(AccidentStatus.WRITTEN_OFF, null)));
        assertEquals(AccidentStatus.REPORTED, a.getStatus());
    }

    @Test void aCaseCannotBeClosedWhileTheClaimIsOpen() {
        Accident a = existing();
        service.updateClaim(tenant, a.getId(), new UpdateAccidentClaimRequest(ClaimStatus.SUBMITTED, "Santam", "CL-1",
                null, null, null, null, null, null));
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                service.changeStatus(tenant, a.getId(), new ChangeAccidentStatusRequest(AccidentStatus.CLOSED, "done")));
        assertTrue(e.getMessage().contains("claim"));
    }

    @Test void aClosedCaseCanBeReopenedToUnderAssessmentOnly() {
        Accident a = existing();
        service.changeStatus(tenant, a.getId(), new ChangeAccidentStatusRequest(AccidentStatus.CLOSED, "no claim"));
        assertEquals(AccidentStatus.CLOSED, a.getStatus());
        assertNotNull(a.getClosedAt());
        assertThrows(IllegalStateException.class, () ->
                service.changeStatus(tenant, a.getId(), new ChangeAccidentStatusRequest(AccidentStatus.IN_REPAIR, null)));
        service.changeStatus(tenant, a.getId(), new ChangeAccidentStatusRequest(AccidentStatus.UNDER_ASSESSMENT, null));
        assertNull(a.getClosedAt());
    }

    @Test void aClaimCannotJumpStraightToSettled() {
        Accident a = existing();
        assertThrows(IllegalStateException.class, () -> service.updateClaim(tenant, a.getId(),
                new UpdateAccidentClaimRequest(ClaimStatus.SETTLED, "Santam", "CL-1", null, null, null, null,
                        new BigDecimal("9000.00"), null)));
    }

    @Test void aLodgedClaimNeedsAnInsurer() {
        Accident a = existing();
        assertThrows(IllegalArgumentException.class, () -> service.updateClaim(tenant, a.getId(),
                new UpdateAccidentClaimRequest(ClaimStatus.SUBMITTED, null, null, null, null, null, null, null, null)));
    }

    @Test void theRepairCannotBeBackBeforeTheAccident() {
        Accident a = existing();
        assertThrows(IllegalArgumentException.class, () -> service.updateRepair(tenant, a.getId(),
                new UpdateAccidentRepairRequest("Panel beaters", new BigDecimal("8000.00"), LocalDate.now().minusDays(30))));
    }

    @Test void detailsCannotBeEditedOnceClosed() {
        Accident a = existing();
        service.changeStatus(tenant, a.getId(), new ChangeAccidentStatusRequest(AccidentStatus.CLOSED, "done"));
        assertThrows(IllegalStateException.class, () -> service.updateDetails(tenant, a.getId(),
                details(Instant.now().minus(1, ChronoUnit.DAYS), null, null, null)));
    }

    @Test void anUnknownAccidentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(accidents.findByTenantAndId(tenant, id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(tenant, id));
    }

    // ── Evidence ─────────────────────────────────────────────────────────

    @Test void evidenceIsStoredThroughTheSharedStorageAndRecorded() {
        Accident a = existing();
        MockMultipartFile file = new MockMultipartFile("file", "../damage photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(uploads.validate(eq(file), any(), anyLong())).thenReturn("image/jpeg");
        when(uploads.sanitiseFileName("../damage photo.jpg")).thenReturn("damage photo.jpg");
        when(uploads.store(any(), eq(file), eq("image/jpeg"))).thenReturn("fleet/key-1");

        AccidentEvidenceResponse r = service.uploadEvidence(tenant, a.getId(), user, file, AccidentEvidenceType.PHOTO, "  rear  ");

        assertEquals("damage photo.jpg", r.fileName());
        assertEquals("PHOTO", r.type());
        assertEquals("rear", r.description());
        ArgumentCaptor<AccidentEvidence> saved = ArgumentCaptor.forClass(AccidentEvidence.class);
        verify(evidence).save(saved.capture());
        assertEquals("fleet/key-1", saved.getValue().getStorageKey());
        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        verify(uploads).store(prefix.capture(), eq(file), eq("image/jpeg"));
        assertTrue(prefix.getValue().startsWith("fleet/" + tenant.getValue()));
        assertTrue(prefix.getValue().endsWith("/accidents/" + a.getId()));
    }

    @Test void aCaseHoldsAtMostFiftyFiles() {
        Accident a = existing();
        when(evidence.countActiveByAccident(tenant, a.getId())).thenReturn(50L);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () ->
                service.uploadEvidence(tenant, a.getId(), user, file, AccidentEvidenceType.OTHER, null));
        verify(uploads, never()).store(any(), any(), any());
    }

    @Test void removingEvidenceKeepsTheRowAndTheStoredFile() {
        Accident a = existing();
        AccidentEvidence ev = AccidentEvidence.create(tenant, a.getId(), AccidentEvidenceType.PHOTO, null, "p.jpg",
                "image/jpeg", 3, "fleet/key-1", user);
        when(evidence.findActiveById(tenant, a.getId(), ev.getId())).thenReturn(Optional.of(ev));
        service.deleteEvidence(tenant, a.getId(), ev.getId(), user);
        assertNotNull(ev.getDeletedAt());
        verify(evidence).save(ev);
        verify(uploads, never()).deleteQuietly(any());
    }

    @Test void downloadingEvidenceOfAnotherAccidentIsNotFound() {
        Accident a = existing();
        UUID evId = UUID.randomUUID();
        when(evidence.findActiveById(tenant, a.getId(), evId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.downloadEvidence(tenant, a.getId(), evId));
    }
}
