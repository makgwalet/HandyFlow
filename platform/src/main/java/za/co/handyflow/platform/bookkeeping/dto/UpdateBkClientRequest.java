package za.co.handyflow.platform.bookkeeping.dto;

public record UpdateBkClientRequest(
        String tradingName, String registrationNumber, String vatNumber, String contactName,
        String contactEmail, String contactPhone, String address
) {}
