package za.co.handyflow.platform.supplychain.dto;

public record CreateLocationRequest(
        String name,
        String locationType,
        String address,
        Boolean isDefault
) {}
