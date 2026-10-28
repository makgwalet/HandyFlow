package za.co.handyflow.platform.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** An employee's own training: where they are booked and what they hold. No other person's data is in it. */
public record MyTrainingResponse(List<Booking> bookings, List<Certificate> certificates) {

    public record Booking(UUID id, String courseTitle, LocalDate startDate, LocalDate endDate, String venue,
                          String status, String statusLabel, BigDecimal score, Boolean passed) {}

    public record Certificate(UUID id, String courseTitle, String number, LocalDate issueDate, LocalDate expiryDate,
                              String state, Long daysLeft) {}
}
