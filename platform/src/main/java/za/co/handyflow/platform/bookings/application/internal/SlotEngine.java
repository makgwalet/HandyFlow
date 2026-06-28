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
                                                 LocalDate date, int durationMinutes,
                                                 int bufferBeforeMinutes,
                                                 int bufferAfterMinutes,
                                                 int minLeadTimeMinutes) {
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

            // Check booking conflict — extend slotEnd by bufferAfterMinutes so
            // the cleanup window is also protected from new bookings
            LocalTime effectiveEnd = slotEnd.plusMinutes(bufferAfterMinutes);
            List<Booking> conflicts = staffId != null
                    ? bookingRepo.findConflicts(staffId, date, slotStart, effectiveEnd)
                    : List.of();

            // Lead time check — suppress slots that start too soon
            boolean tooSoon = false;
            if (minLeadTimeMinutes > 0 && date.equals(LocalDate.now())) {
                LocalTime cutoff = LocalTime.now().plusMinutes(minLeadTimeMinutes);
                tooSoon = slotStart.isBefore(cutoff);
            }

            if (!blocked && conflicts.isEmpty() && !tooSoon) {
                String label = slotStart.format(TIME_FMT) + " – " + slotEnd.format(TIME_FMT);
                slots.add(new AvailableSlot(slotStart, slotEnd, label));
            }

            // Step by max(SLOT_INTERVAL, bufferBefore) so back-to-back slots
            // respect prep time between appointments
            int step = Math.max(SLOT_INTERVAL_MINUTES, bufferBeforeMinutes > 0 ? bufferBeforeMinutes : SLOT_INTERVAL_MINUTES);
            cursor = cursor.plusMinutes(step);
        }

        log.debug("Found {} slots for staffId={} date={}", slots.size(), staffId, date);
        return slots;
    }
}