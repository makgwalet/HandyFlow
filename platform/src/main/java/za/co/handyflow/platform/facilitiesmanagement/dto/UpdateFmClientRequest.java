package za.co.handyflow.platform.facilitiesmanagement.dto;

public record UpdateFmClientRequest(
        String tradingName, String registrationNumber, String contactName,
        String contactEmail, String contactPhone, String address,
        /** null = leave unchanged, blank = clear, otherwise 10 digits starting with 4. */
        String vatNumber
) {}
