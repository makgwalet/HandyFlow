package za.co.handyflow.platform.bookings.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.bookings.domain.model.Booking;
import za.co.handyflow.platform.bookings.domain.model.BookingAvailability;
import za.co.handyflow.platform.bookings.domain.model.BookingBlock;
import za.co.handyflow.platform.bookings.domain.repository.*;
import za.co.handyflow.platform.bookings.dto.AvailableSlot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlotEngine {

    private final BookingAvailabilityRepository availabilityRepo;
    private final BookingBlockRepository        blockRepo;
    private final BookingRepository             bookingRepo;

    private static final int SLOT_INTERVAL_MINUTES = 30;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    public List<AvailableSlot> getAvailableSlots(TenantId tenantId, UUID staffId,
                                                 LocalDate date, int durationMinutes) {
        // WHY get day of week as int matching our schema?
        // Java DayOfWeek: MONDAY=1 … SUNDAY=7
        // Our schema: 0=Sun, 1=Mon … 6=Sat
        int javaDow = date.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        int schemaDow = javaDow % 7;                  // 1=Mon … 6=Sat, 0=Sun

        // 1. Get working hours for this staff + day
        List<BookingAvailability> availability = staffId != null
                ? availabilityRepo.findForStaff(tenantId, staffId)
                : availabilityRepo.findTenantWide(tenantId);

        BookingAvailability workingHours = availability.stream()
                .filter(a -> a.getDayOfWeek() == schemaDow)
                .findFirst()
                .orElse(null);

        if (workingHours == null) {
            log.debug("No working hours for staffId={} on dow={}", staffId, schemaDow);
            return List.of(); // not a working day
        }

        // 2. Get blocks for this day
        List<BookingBlock> blocks = blockRepo.findForStaffOnDate(tenantId, staffId, date);

        // 3. Check full day block
        boolean fullDayBlocked = blocks.stream().anyMatch(BookingBlock::isFullDay);
        if (fullDayBlocked) return List.of();

        // 4. Get existing bookings for conflict check
        // We'll check per-slot below using the repository

        // 5. Generate candidate slots
        List<AvailableSlot> slots = new ArrayList<>();
        LocalTime cursor = workingHours.getStartTime();
        LocalTime workEnd = workingHours.getEndTime();

        while (!cursor.plusMinutes(durationMinutes).isAfter(workEnd)) {
            LocalTime slotStart = cursor;                              // effectively final capture
            LocalTime slotEnd   = slotStart.plusMinutes(durationMinutes);

            // Check block overlap
            boolean blocked = blocks.stream()
                    .anyMatch(b -> b.overlaps(slotStart, slotEnd));

            // Check booking conflict
            List<Booking> conflicts = staffId != null
                    ? bookingRepo.findConflicts(staffId, date, slotStart, slotEnd)
                    : List.of();

            if (!blocked && conflicts.isEmpty()) {
                String label = slotStart.format(TIME_FMT) + " – " + slotEnd.format(TIME_FMT);
                slots.add(new AvailableSlot(slotStart, slotEnd, label));
            }

            cursor = cursor.plusMinutes(SLOT_INTERVAL_MINUTES);
        }

        log.debug("Found {} slots for staffId={} date={}", slots.size(), staffId, date);
        return slots;
    }
}