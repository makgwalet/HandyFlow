package za.co.handyflow.platform.agriculture.dto;

import java.time.Instant;

/** Who added a record and who last changed it. The names are null for records from before this was kept. */
public record AuditResponse(String createdByName, Instant createdAt, String updatedByName, Instant updatedAt) {}
