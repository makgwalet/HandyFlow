package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** A non-working day on the tenant's business calendar. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmHolidayEntry {

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(name = "name", length = 100)
    private String name;

    public static FmHolidayEntry of(LocalDate date, String name) {
        FmHolidayEntry h = new FmHolidayEntry();
        h.date = date;
        h.name = name;
        return h;
    }
}