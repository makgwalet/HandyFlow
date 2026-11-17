package za.co.handyflow.platform.supplychain.dto;

import java.time.Instant;
import java.util.UUID;

public record SupplierEventResponse(UUID id, String eventType, String fromStatus, String toStatus,
                                    String detail, String actorName, Instant createdAt) {}
