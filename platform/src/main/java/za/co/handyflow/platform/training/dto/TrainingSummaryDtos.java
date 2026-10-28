package za.co.handyflow.platform.training.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TrainingSummaryDtos {
    private TrainingSummaryDtos() {}

    public record UpcomingSession(UUID id, String courseTitle, LocalDate startDate, LocalDate endDate, long enrolled, Integer capacity) {}
    public record ExpiringCertificate(UUID id, String employeeName, String courseTitle, LocalDate expiryDate, long daysLeft) {}
    /** {@code lapsed} counts valid certificates already past their expiry date. */
    public record Summary(long activeCourses, long scheduledSessions, long liveEnrollments, long expiringSoon, long lapsed,
                          List<UpcomingSession> upcoming, List<ExpiringCertificate> expiring) {}
}
