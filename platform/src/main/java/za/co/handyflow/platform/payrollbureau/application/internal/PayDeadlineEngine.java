package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClient;
import za.co.handyflow.platform.payrollbureau.domain.model.PayDeadline;
import za.co.handyflow.platform.shared.PublicHolidayRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SARS deadline generation for payroll bureau clients — EMP201 and
 * EMP501 only (the two deadline types actually relevant to running
 * payroll; VAT201/ITR14/IRP6 are accounting-side concerns, not this
 * module's job).
 * <p>
 * DELIBERATELY NOT A RETROFIT OF accountant.DeadlineEngine: that engine
 * takes an AccClient, which has vatCategory/yearEndMonth — fields a
 * PayClient doesn't have and shouldn't need. Bending DeadlineEngine to
 * accept two different client shapes (an if/else or a common interface
 * just for two fields) would complicate a working, correct engine for
 * the sake of avoiding ~30 lines of date math here — the same
 * "don't generalize from one example" reasoning already applied to the
 * Bookings/Scheduling decision at Q15. What IS shared: the actual due
 * DATE rules themselves (verified against DeadlineEngine's real code,
 * not reconstructed from memory) and the public-holiday reference data
 * (via shared.PublicHoliday — see that class's Javadoc).
 * <p>
 * RULES (confirmed against accountant.DeadlineEngine's actual
 * implementation, not assumed):
 *   EMP201 — 7th of each month; if weekend/public holiday, prior business day
 *   EMP501 — 31 May annually; same business-day adjustment
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayDeadlineEngine {

    private final PublicHolidayRepository holidayRepo;

    public List<PayDeadline> generateForClient(PayClient client, int year) {
        List<PayDeadline> deadlines = new ArrayList<>();
        UUID tenantId = client.getTenantId();
        UUID clientId = client.getId();

        // EMP201 — monthly, 7th of each month
        for (int month = 1; month <= 12; month++) {
            LocalDate raw = LocalDate.of(year, month, 7);
            LocalDate adjusted = adjustForBusinessDay(raw);
            deadlines.add(PayDeadline.create(tenantId, clientId, "EMP201", year, month, raw, adjusted));
        }

        // EMP501 — annual, 31 May
        LocalDate raw = LocalDate.of(year, 5, 31);
        LocalDate adjusted = adjustForBusinessDay(raw);
        deadlines.add(PayDeadline.create(tenantId, clientId, "EMP501", year, null, raw, adjusted));

        log.info("Generated {} payroll deadlines for client={} year={}", deadlines.size(), clientId, year);
        return deadlines;
    }

    LocalDate adjustForBusinessDay(LocalDate date) {
        LocalDate adjusted = date;
        while (isWeekend(adjusted) || holidayRepo.existsByDate(adjusted)) {
            adjusted = adjusted.minusDays(1);
        }
        return adjusted;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}