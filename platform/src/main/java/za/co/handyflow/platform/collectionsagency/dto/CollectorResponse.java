package za.co.handyflow.platform.collectionsagency.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CollectorResponse(
        UUID id, UUID userId, String fullName, String registrationNumber, LocalDate registrationExpiryDate,
        String email, String phone, boolean active
) {}
