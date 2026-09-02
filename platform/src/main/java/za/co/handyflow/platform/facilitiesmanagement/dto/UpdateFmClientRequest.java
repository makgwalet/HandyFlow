package za.co.handyflow.platform.facilitiesmanagement.dto;

public record UpdateFmClientRequest(
        String tradingName, String registrationNumber, String contactName,
        String contactEmail, String contactPhone, String address
) {}
