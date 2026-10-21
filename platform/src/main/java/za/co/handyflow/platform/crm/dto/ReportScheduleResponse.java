package za.co.handyflow.platform.crm.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ReportFrequency;
import za.co.handyflow.platform.crm.domain.model.ReportType;

public record ReportScheduleResponse(UUID id, String name, ReportType reportType, ReportFrequency frequency, List<UUID> recipientIds, boolean active, Instant lastSentAt, String description) {}
