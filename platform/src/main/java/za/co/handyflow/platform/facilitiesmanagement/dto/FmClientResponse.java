package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.UUID;

public record FmClientResponse(
        UUID id, String clientCode, String tradingName, String registrationNumber, String contactName,
        String contactEmail, String contactPhone, String address, String status, Instant createdAt
) {}
