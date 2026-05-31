package za.co.handyflow.platform.bookings.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String bookingNumber,
        UUID serviceId,
        String serviceName,
        UUID staffId,
        String staffName,
        String clientName,
        String clientEmail,
        String clientPhone,
        LocalDate bookingDate,
        LocalTime startTime,
        LocalTime endTime,
        int durationMinutes,
        String status,
        BigDecimal price,
        String notes,
        UUID invoiceId,
        Instant confirmedAt,
        Instant completedAt,
        Instant createdAt
) {}