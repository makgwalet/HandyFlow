package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.List;

/** One page of the patient directory: the patient plus what the front desk needs to see at a glance. */
public record PatientDirectoryPage(List<Entry> content, int page, int size, long total) {
    public record Entry(
            PatientResponse patient,
            int visitCount,
            Instant lastVisitAt,
            Instant nextAppointmentAt,
            /** Active patients sharing this first and last name (1 = unique). */
            int sameNameCount,
            /** Has an open follow-up recall. */
            boolean followUpDue
    ) {}
}
