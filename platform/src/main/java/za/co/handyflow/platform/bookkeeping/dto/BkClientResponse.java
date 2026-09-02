package za.co.handyflow.platform.bookkeeping.dto;

import java.time.Instant;
import java.util.UUID;

public record BkClientResponse(
        UUID id, String clientCode, String tradingName, String registrationNumber, String vatNumber,
        String contactName, String contactEmail, String contactPhone, String address, String status, Instant createdAt
) {}
