package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.bookings.application.BookingsFacade;
import za.co.handyflow.platform.bookings.application.BookingsFacade.BookingInfo;
import za.co.handyflow.platform.bookings.application.BookingsFacade.ResourceInfo;
import za.co.handyflow.platform.bookings.application.BookingsFacade.ServiceInfo;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopOffering;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopReservation;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopOfferingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopReservationRepository;
import za.co.handyflow.platform.fuel.dto.TruckStopDtos.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTruckStopServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    @Mock FuelTruckStopOfferingRepository offerings;
    @Mock FuelTruckStopReservationRepository reservations;
    @Mock FuelDepotRepository depots;
    @Mock BookingsFacade bookings;
    @InjectMocks FuelTruckStopService service;

    UUID serviceId = UUID.randomUUID(), bayId = UUID.randomUUID();
    ServiceInfo shower;
    FuelTruckStopOffering offering;

    private BookingInfo booking(String status) {
        return new BookingInfo(UUID.randomUUID(), "BK-2026-00001", serviceId, "Shower", bayId, "Bay 1", "Moses (CY 1 GP)", "082", LocalDate.of(2026, 11, 14),
                LocalTime.of(9, 0), LocalTime.of(9, 30), status, new BigDecimal("40.00"), null);
    }

    @BeforeEach
    void setUp() {
        shower = new ServiceInfo(serviceId, "Shower", 30, new BigDecimal("40.00"), true);
        when(bookings.services(T)).thenReturn(List.of(shower));
        when(bookings.resources(T, serviceId)).thenReturn(List.of());
        offering = FuelTruckStopOffering.create(T, serviceId, "shower", null, USER);
        when(offerings.findOwned(T, offering.getId())).thenReturn(Optional.of(offering));
        when(offerings.findAllForTenant(T)).thenReturn(List.of(offering));
    }

    private ReserveRequest req(UUID resource) {
        return new ReserveRequest(offering.getId(), resource, LocalDate.of(2026, 11, 14), LocalTime.of(9, 0), "cy 1 gp", "Moses", "082", null);
    }

    @Test @DisplayName("a service must exist in Bookings and be sold only once")
    void addOffering() {
        UUID other = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class, () -> service.addOffering(T, new OfferingRequest(other, "SHOWER", null), USER));
        when(offerings.serviceTaken(T, serviceId)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.addOffering(T, new OfferingRequest(serviceId, "SHOWER", null), USER));
        verify(offerings, never()).save(any());
    }

    @Test @DisplayName("an unknown kind is refused and the kind is stored in capitals")
    void kinds() {
        assertThrows(IllegalArgumentException.class, () -> FuelTruckStopOffering.create(T, serviceId, "SPA", null, USER));
        assertEquals("SHOWER", offering.getKind());
    }

    @Test @DisplayName("reserving books through Bookings, then keeps the truck and driver; the registration is stored in capitals")
    void reserves() {
        BookingInfo b = booking("PENDING");
        when(bookings.reserve(eq(T), eq(serviceId), any(), any(), any(), any(), any(), any(), any())).thenReturn(b);
        ReservationResponse r = service.reserve(T, req(null), USER);
        assertEquals("CY 1 GP", r.vehicleRegistration());
        assertEquals(b.id(), r.bookingId());
        assertEquals("SHOWER", r.kind());
        verify(reservations).save(any(FuelTruckStopReservation.class));
    }

    @Test @DisplayName("a switched-off service cannot be reserved")
    void inactive() {
        offering.setActive(false);
        assertThrows(ConflictException.class, () -> service.reserve(T, req(null), USER));
        verify(bookings, never()).reserve(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("a service with bays must be reserved against one of them, so Bookings can stop double booking")
    void needsABay() {
        when(bookings.resources(T, serviceId)).thenReturn(List.of(new ResourceInfo(bayId, "Bay 1")));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(T, req(null), USER));
        assertThrows(IllegalArgumentException.class, () -> service.reserve(T, req(UUID.randomUUID()), USER));
        verify(bookings, never()).reserve(any(), any(), any(), any(), any(), any(), any(), any(), any());
        when(bookings.reserve(eq(T), eq(serviceId), eq(bayId), any(), any(), any(), any(), any(), any())).thenReturn(booking("PENDING"));
        assertEquals("Bay 1", service.reserve(T, req(bayId), USER).resourceName());
    }

    @Test @DisplayName("a missing registration is refused before Bookings is touched")
    void registrationRequired() {
        ReserveRequest bad = new ReserveRequest(offering.getId(), null, LocalDate.of(2026, 11, 14), LocalTime.of(9, 0), " ", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.reserve(T, bad, USER));
        verifyNoInteractions(reservations);
        verify(bookings, never()).reserve(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("a Bookings refusal (slot taken) stops the reservation and nothing of ours is saved")
    void bookingsRefuses() {
        when(bookings.reserve(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenThrow(new ConflictException("This time slot is no longer available."));
        assertThrows(ConflictException.class, () -> service.reserve(T, req(null), USER));
        verify(reservations, never()).save(any());
    }

    @Test @DisplayName("another company's reservation is not found, and Bookings is not asked to cancel it")
    void tenantScoping() {
        UUID foreign = UUID.randomUUID();
        when(reservations.findOwned(T, foreign)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(T, foreign, "x"));
        verify(bookings, never()).cancel(any(), any(), any());
    }

    @Test @DisplayName("cancel, complete and no-show go through Bookings for the linked booking")
    void outcomes() {
        FuelTruckStopReservation r = FuelTruckStopReservation.create(T, offering.getId(), UUID.randomUUID(), "CY 1", null, null, USER);
        when(reservations.findOwned(T, r.getId())).thenReturn(Optional.of(r));
        when(bookings.cancel(eq(T), eq(r.getBookingId()), any())).thenReturn(booking("CANCELLED"));
        when(bookings.complete(T, r.getBookingId())).thenReturn(booking("COMPLETED"));
        when(bookings.noShow(T, r.getBookingId())).thenReturn(booking("NO_SHOW"));
        assertEquals("CANCELLED", service.cancel(T, r.getId(), null).status());
        assertEquals("COMPLETED", service.complete(T, r.getId()).status());
        assertEquals("NO_SHOW", service.noShow(T, r.getId()).status());
    }

    @Test @DisplayName("a booking removed in Bookings does not break the list; the truck still shows")
    void removedBooking() {
        FuelTruckStopReservation r = FuelTruckStopReservation.create(T, offering.getId(), UUID.randomUUID(), "CY 1", null, null, USER);
        when(reservations.findRecent(eq(T), any())).thenReturn(List.of(r));
        when(bookings.get(T, r.getBookingId())).thenThrow(new ResourceNotFoundException("Booking", "x"));
        List<ReservationResponse> out = service.recent(T, null, 50);
        assertEquals("UNKNOWN", out.get(0).status());
        assertEquals("CY 1", out.get(0).vehicleRegistration());
    }

    @Test @DisplayName("the picker lists only active Bookings services that are not already offered")
    void availableServices() {
        assertTrue(service.availableServices(T).isEmpty());
        when(offerings.findAllForTenant(T)).thenReturn(List.of());
        assertEquals(1, service.availableServices(T).size());
    }
}
