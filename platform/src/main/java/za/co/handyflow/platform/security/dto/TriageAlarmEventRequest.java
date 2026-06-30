package za.co.handyflow.platform.security.dto;

public record TriageAlarmEventRequest(
        String severity   // optional override — LOW | MEDIUM | HIGH | CRITICAL
) {}
