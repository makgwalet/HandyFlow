package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Live picture of the in-house facilities function, computed on request within the caller's access scope.
 * <ul>
 *   <li><b>open</b>: OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD and PENDING_VERIFICATION work. Requests still waiting for
 *       approval are counted separately in {@code awaitingApproval}.</li>
 *   <li><b>overdue</b>: past the scheduled date and neither finished nor awaiting a verifier.</li>
 *   <li>The 30-day figures cover work COMPLETED in the last 30 days. {@code reworkRatePercent} is the share of that
 *       work that had been sent back at least once; null when nothing was completed.</li>
 *   <li>{@code ppmSchedulesOverdue}: active PPM schedules past their next-due date. Certificates: valid ones
 *       expiring in the next 30 days, and those already past expiry (not revoked).</li>
 * </ul>
 */
public record FacilityDashboardResponse(Instant generatedAt, long open, long unassigned, long onHold, long overdue,
                                        long awaitingApproval, long awaitingVerification,
                                        Map<String, Long> openByStatus, Map<String, Long> openByPriority,
                                        long completedLast30Days, BigDecimal avgCompletionHoursLast30Days,
                                        BigDecimal reworkRatePercentLast30Days, long downtimeMinutesLast30Days,
                                        BigDecimal costLast30Days,
                                        long ppmSchedulesOverdue, long certificatesExpiringIn30Days, long certificatesExpired) {}
