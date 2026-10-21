package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.ReportFrequency;
import za.co.handyflow.platform.crm.domain.model.ReportType;

/** recipientIds may be empty: the report then goes to the account's admins. */
public record ReportScheduleRequest(String name, ReportType reportType, ReportFrequency frequency, List<UUID> recipientIds, Boolean active) {}
