package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The Fuel dashboard's figures, worked out on the server over the whole month and the whole set of open deliveries (not a page of a list), and limited to
 * the tanks the caller may see. The month is the calendar month in South African time. Tank levels stay on the tanks list.
 *
 * @param dailyLitres litres dispatched on each day of the month up to today (index 0 is the 1st)
 * @param negativeVarianceReadings dip readings this month more than 5 L below the book figure
 * @param overdueDeliveries scheduled more than a day ago and not yet on the road
 * @param nextDeliveries the soonest open deliveries, at most 8
 */
public record FuelDashboardSummary(LocalDate today, BigDecimal monthLitres, int monthDispatches, int monthReceipts, List<BigDecimal> dailyLitres,
                                   int negativeVarianceReadings, int openDeliveries, int inTransitDeliveries, int overdueDeliveries,
                                   List<NextDelivery> nextDeliveries) {

    public record NextDelivery(UUID id, String status, String fuelType, BigDecimal litresOrdered, Instant scheduledAt) {}
}
