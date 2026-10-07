package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the clinic dashboard shows, worked out on the server for the clinic's own (South African) day. */
public record DashboardSummary(
        String date,
        String timeZone,
        int todayTotal,
        int awaiting,
        int inProgress,
        int completed,
        int cancelled,
        int noShow,
        long totalPatients,
        Map<String, Integer> byStatus,
        List<Item> today,
        Item next
) {
    /** One appointment line. */
    public record Item(UUID id, String patientName, String practitionerName, Instant scheduledAt,
                       int durationMinutes, String appointmentType, String status) {}
}
