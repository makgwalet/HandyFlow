package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBkClientRequest(
        @NotBlank String tradingName, String registrationNumber, String vatNumber, String contactName,
        String contactEmail, String contactPhone, String address
) {}
