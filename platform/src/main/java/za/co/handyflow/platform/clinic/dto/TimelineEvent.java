package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One entry in a patient's timeline. {@code kind} is APPOINTMENT, CONSULTATION, PRESCRIPTION, LAB, CLAIM or PAYMENT.
 * A consultation also carries a short {@code summary} (reason, diagnosis, plan, follow-up) and the {@code people}
 * involved ("Prepared by ...", "Signed by ..."); both are empty for other kinds.
 */
public record TimelineEvent(String kind, UUID id, Instant at, String title, String detail, String status,
                            List<String> summary, List<String> people) {
    public TimelineEvent(String kind, UUID id, Instant at, String title, String detail, String status) {
        this(kind, id, at, title, detail, status, List.of(), List.of());
    }

    public TimelineEvent withVisit(List<String> summary, List<String> people) {
        return new TimelineEvent(kind, id, at, title, detail, status, summary, people);
    }
}
