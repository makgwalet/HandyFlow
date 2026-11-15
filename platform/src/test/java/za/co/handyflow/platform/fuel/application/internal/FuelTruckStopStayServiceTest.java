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
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopParking;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopParkingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.dto.StayDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 7d: overnight stays: nights, the rate kept at booking, bay capacity and the lifecycle. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTruckStopStayServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelTruckStopStayRepository stays;
    @Mock FuelTruckStopParkingRepository parking;
    @InjectMocks FuelTruckStopStayService service;

    final List<FuelTruckStopStay> saved = new ArrayList<>();
    FuelTruckStopParking settings;

    @BeforeEach
    void setUp() {
        settings = null;
        when(parking.findForTenant(T)).thenAnswer(i -> Optional.ofNullable(settings));
        when(parking.save(any())).thenAnswer(i -> { settings = i.getArgument(0); return settings; });
        when(stays.findRecent(eq(T), any())).thenAnswer(i -> new ArrayList<>(saved));
        when(stays.save(any())).thenAnswer(i -> { FuelTruckStopStay s = i.getArgument(0); if (!saved.contains(s)) saved.add(s); return s; });
        when(stays.findOwned(eq(T), any())).thenAnswer(i -> saved.stream().filter(s -> s.getId().equals(i.getArgument(1))).findFirst());
        // Same rule as the repository query: holding a bay, starting before 'to', and either ending after 'from' or still in the yard.
        when(stays.findHoldingBays(eq(T), any(), any())).thenAnswer(i -> {
            LocalDate from = i.getArgument(1), to = i.getArgument(2);
            return saved.stream().filter(s -> s.holdsBay() && s.getArrivalDate().isBefore(to) && (s.getDepartureDate().isAfter(from) || "CHECKED_IN".equals(s.getStatus()))).toList();
        });
    }

    private void rate(String r, Integer bays) { service.saveSettings(T, new SettingsRequest(r == null ? null : bd(r), bays), BY); }
    private BookRequest req(String reg, int fromOffset, int toOffset) { return new BookRequest(reg, "Sipho", "0821234567", TODAY.plusDays(fromOffset), TODAY.plusDays(toOffset), null); }
    private StayView book(String reg, int fromOffset, int toOffset) { return service.book(T, req(reg, fromOffset, toOffset), BY, TODAY); }

    @Test @DisplayName("nothing can be booked until the company sets a nightly rate")
    void needsRate() {
        assertThrows(IllegalStateException.class, () -> book("GP 1", 0, 2));
        rate(null, null);
        assertThrows(IllegalStateException.class, () -> book("GP 1", 0, 2));
        rate("150", null);
        assertEquals("BOOKED", book("GP 1", 0, 2).status());
    }

    @Test @DisplayName("a stay keeps the rate it was booked at when the rate changes later")
    void keepsRate() {
        rate("150", null);
        StayView first = book("GP 1", 0, 2);
        rate("200", null);
        StayView second = book("GP 2", 0, 2);
        assertEquals(0, bd("150").compareTo(service.list(T, 10, TODAY).stream().filter(v -> v.id().equals(first.id())).findFirst().orElseThrow().ratePerNight()));
        assertEquals(0, bd("200").compareTo(second.ratePerNight()));
        assertEquals(0, bd("300.00").compareTo(first.amount()));
    }

    @Test @DisplayName("nights run from arrival to departure with at least one, and a check-out on a different day re-counts them")
    void nightsAndCheckOut() {
        rate("100", null);
        StayView v = book("GP 1", 0, 3);
        assertEquals(3, v.nights()); assertEquals(0, bd("300.00").compareTo(v.amount()));
        service.checkIn(T, v.id(), TODAY);
        StayView out = service.checkOut(T, v.id(), new CheckOutRequest(TODAY.plusDays(2)), TODAY.plusDays(2));
        assertEquals("CHECKED_OUT", out.status()); assertEquals(2, out.nights()); assertEquals(0, bd("200.00").compareTo(out.amount()));
        StayView same = book("GP 2", 0, 1);
        service.checkIn(T, same.id(), TODAY);
        assertEquals(1, service.checkOut(T, same.id(), null, TODAY).nights());   // same-day departure still counts one night
    }

    @Test @DisplayName("a past arrival, a departure not after the arrival and a missing registration are refused")
    void bookingRefusals() {
        rate("100", null);
        assertThrows(IllegalArgumentException.class, () -> book("GP 1", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> book("GP 1", 2, 2));
        assertThrows(IllegalArgumentException.class, () -> service.book(T, new BookRequest(" ", null, null, TODAY, TODAY.plusDays(1), null), BY, TODAY));
        assertTrue(saved.isEmpty());
    }

    @Test @DisplayName("with a bay count, a night with every bay taken cannot be booked; other nights and cancelled stays do not count")
    void capacity() {
        rate("100", 2);
        StayView a = book("GP 1", 0, 3); book("GP 2", 1, 3);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> book("GP 3", 1, 2));
        assertTrue(e.getMessage().contains(TODAY.plusDays(1).toString()));
        assertEquals("BOOKED", book("GP 3", 3, 4).status());      // the night after the others leave
        service.cancel(T, a.id(), new CancelRequest("Changed plans"), TODAY);
        assertEquals("BOOKED", book("GP 4", 1, 2).status());      // the cancelled stay released its bay
    }

    @Test @DisplayName("with no bay count nothing is capacity-limited")
    void noLimit() {
        rate("100", null);
        for (int i = 0; i < 6; i++) book("GP " + i, 0, 2);
        assertEquals(6, saved.size());
    }

    @Test @DisplayName("a truck still in the yard after its planned departure keeps its bay up to today")
    void overstayHoldsBay() {
        rate("100", 1);
        StayView v = book("GP 1", 0, 1);
        service.checkIn(T, v.id(), TODAY);
        LocalDate later = TODAY.plusDays(3);
        StayView seen = service.list(T, 10, later).get(0);
        assertTrue(seen.overstaying());
        assertEquals(1, service.occupancy(T, later).inYard());
        assertThrows(IllegalStateException.class, () -> service.book(T, new BookRequest("GP 2", null, null, later, later.plusDays(1), null), BY, later));
        service.checkOut(T, v.id(), new CheckOutRequest(later), later);
        assertEquals("BOOKED", service.book(T, new BookRequest("GP 2", null, null, later, later.plusDays(1), null), BY, later).status());
    }

    @Test @DisplayName("extending needs a free bay on each added night; shortening does not, and the departure must stay after the arrival")
    void changeDeparture() {
        rate("100", 1);
        StayView a = book("GP 1", 0, 2);
        book("GP 2", 2, 4);
        assertThrows(IllegalStateException.class, () -> service.changeDeparture(T, a.id(), new DepartureRequest(TODAY.plusDays(3)), TODAY));
        assertEquals(1, service.changeDeparture(T, a.id(), new DepartureRequest(TODAY.plusDays(1)), TODAY).nights());
        assertThrows(IllegalArgumentException.class, () -> service.changeDeparture(T, a.id(), new DepartureRequest(TODAY), TODAY));
    }

    @Test @DisplayName("the lifecycle only moves one way: check in before the arrival date, check out before check in, cancel after check in and no-show too early are refused")
    void lifecycle() {
        rate("100", null);
        StayView later = book("GP 1", 2, 3);
        assertThrows(IllegalStateException.class, () -> service.checkIn(T, later.id(), TODAY));
        assertThrows(IllegalStateException.class, () -> service.checkOut(T, later.id(), null, TODAY));
        assertThrows(IllegalStateException.class, () -> service.noShow(T, later.id(), TODAY));
        StayView today = book("GP 2", 0, 1);
        service.checkIn(T, today.id(), TODAY);
        assertThrows(IllegalStateException.class, () -> service.cancel(T, today.id(), new CancelRequest("x"), TODAY));
        assertThrows(IllegalStateException.class, () -> service.checkIn(T, today.id(), TODAY));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(T, later.id(), new CancelRequest(" "), TODAY));
        assertEquals("NO_SHOW", service.noShow(T, later.id(), TODAY.plusDays(2)).status());
        assertThrows(ResourceNotFoundException.class, () -> service.checkIn(T, UUID.randomUUID(), TODAY));
    }

    @Test @DisplayName("occupancy counts trucks in the yard, arrivals and departures due, and the bays free")
    void occupancyCounts() {
        rate("100", 5);
        StayView in = book("GP 1", 0, 2); service.checkIn(T, in.id(), TODAY);
        book("GP 2", 0, 1);                       // arrives today, still booked
        book("GP 3", 1, 2);                       // tomorrow
        Occupancy o = service.occupancy(T, TODAY);
        assertEquals(2, o.inYard()); assertEquals(1, o.arrivals()); assertEquals(0, o.departures()); assertEquals(5, o.bays()); assertEquals(3, o.free());
        assertEquals(1, service.occupancy(T, TODAY.plusDays(1)).departures());
        rate("100", null);
        assertNull(service.occupancy(T, TODAY).bays()); assertNull(service.occupancy(T, TODAY).free());
    }

    @Test @DisplayName("a negative rate or zero bays is refused in the settings")
    void settingsRules() {
        assertThrows(IllegalArgumentException.class, () -> service.saveSettings(T, new SettingsRequest(bd("-1"), null), BY));
        assertThrows(IllegalArgumentException.class, () -> service.saveSettings(T, new SettingsRequest(bd("100"), 0), BY));
        assertNull(service.settings(T).nightlyRate());
    }

    @Test @DisplayName("booking and extending a stay take the company's parking lock first, so the last bay cannot be given twice")
    void takesTheLock() {
        rate("150", 1);
        StayView v = book("GP 1", 0, 2);
        verify(parking).lockForTenant(T);
        service.changeDeparture(T, v.id(), new DepartureRequest(TODAY.plusDays(3)), TODAY);
        verify(parking, times(2)).lockForTenant(T);
    }

    @Test @DisplayName("the capacity view counts bays used and free night by night, with arrivals and departures")
    void capacityView() {
        rate("150", 3);
        book("GP 1", 0, 3);
        book("GP 2", 1, 2);
        CapacityView v = service.capacity(T, TODAY, 4, TODAY);
        assertEquals(3, v.bays());
        assertEquals(4, v.nights().size());
        assertEquals(1, v.nights().get(0).used());
        assertEquals(2, v.nights().get(1).used());
        assertEquals(1, v.nights().get(1).free());
        assertEquals(1, v.nights().get(2).used());
        assertEquals(0, v.nights().get(3).used());
        assertEquals(1, v.nights().get(1).arrivals());
        assertEquals(1, v.nights().get(2).departures());
        assertTrue(v.overstays().isEmpty());
    }

    @Test @DisplayName("with no bay count the capacity view still shows use but no free figure")
    void capacityNoLimit() {
        rate("150", null);
        book("GP 1", 0, 2);
        CapacityView v = service.capacity(T, TODAY, 2, TODAY);
        assertNull(v.bays());
        assertNull(v.nights().get(0).free());
        assertEquals(1, v.nights().get(0).used());
    }

    @Test @DisplayName("a truck in the yard past its planned departure is listed as overstaying with the days over")
    void capacityOverstay() {
        rate("150", 2);
        StayView v = book("GP 1", 0, 1);
        service.checkIn(T, v.id(), TODAY);
        CapacityView c = service.capacity(T, TODAY, 3, TODAY.plusDays(2));
        assertEquals(1, c.overstays().size());
        assertEquals("GP 1", c.overstays().get(0).vehicleRegistration());
        assertEquals(1, c.overstays().get(0).daysOver());
    }

    @Test @DisplayName("the number of nights is held between 1 and 31")
    void capacityDaysLimit() {
        rate("150", 2);
        assertEquals(1, service.capacity(T, TODAY, 0, TODAY).nights().size());
        assertEquals(31, service.capacity(T, TODAY, 400, TODAY).nights().size());
    }
}
