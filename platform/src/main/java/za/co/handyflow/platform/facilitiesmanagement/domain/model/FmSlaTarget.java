package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One SLA commitment of an agreement: for work of {@code priority}, respond and resolve within the given minutes on the given clock. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmSlaTarget {

    @Column(name = "priority", nullable = false, length = 10)
    private String priority; // LOW, NORMAL, HIGH, URGENT, EMERGENCY

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Column(name = "clock", nullable = false, length = 20)
    private String clock; // 24X7 or BUSINESS_HOURS

    public static FmSlaTarget of(String priority, int responseMinutes, int resolutionMinutes, String clock) {
        FmSlaTarget t = new FmSlaTarget();
        t.priority = priority;
        t.responseMinutes = responseMinutes;
        t.resolutionMinutes = resolutionMinutes;
        t.clock = clock;
        return t;
    }
}