package za.co.handyflow.platform.agriculture.dto;

public record UpdateCropTypeRequest(
        String name,
        Integer typicalGrowingDays,
        String defaultUnitOfMeasure
) {}
