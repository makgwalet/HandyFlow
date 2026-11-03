package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.projects.application.internal.schedule.SimpleWorkingCalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the working calendar used for scheduling: configured working weekdays minus the
 * national public holidays (shared PublicHoliday reference data).
 *
 * app.projects.schedule.working-days (default MON,TUE,WED,THU,FRI) — e.g. add SAT for
 * construction sites that work Saturdays. One calendar per deployment for now; per-project
 * and per-site calendars (resource/site constraints) are a later enhancement.
 *
 * Holidays are loaded only for the requested range (+2 years of headroom); beyond that the
 * calendar degrades gracefully to weekdays-only.
 */
@Component
public class ProjectCalendarProvider {

    @PersistenceContext
    private EntityManager em;

    @Value("${app.projects.schedule.working-days:MON,TUE,WED,THU,FRI}")
    private String workingDaysCsv;

    public SimpleWorkingCalendar forRange(LocalDate from, LocalDate to) {
        List<LocalDate> holidays = em.createQuery(
                        "SELECT h.holidayDate FROM PublicHoliday h WHERE h.holidayDate BETWEEN :f AND :t",
                        LocalDate.class)
                .setParameter("f", from.minusDays(7))
                .setParameter("t", to.plusYears(2))
                .getResultList();
        return new SimpleWorkingCalendar(parseWorkingDays(workingDaysCsv), new HashSet<>(holidays));
    }

    /** Accepts MON / MONDAY / mon …; anything unusable falls back to Monday–Friday. */
    static Set<DayOfWeek> parseWorkingDays(String csv) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (csv != null) {
            for (String token : csv.split(",")) {
                String t = token.trim().toUpperCase();
                if (t.length() < 3) continue;
                for (DayOfWeek d : DayOfWeek.values()) {
                    if (d.name().startsWith(t)) days.add(d);
                }
            }
        }
        if (days.isEmpty()) {
            return EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        return days;
    }
}