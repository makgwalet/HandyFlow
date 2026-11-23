package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HealthEventResponse(
        UUID id,
        UUID animalId,
        UUID groupId,
        String eventType,
        LocalDate eventDate,
        String description,
        String productUsed,
        String dosage,
        UUID administeredBy,
        String administeredByName,
        String veterinarian,
        BigDecimal cost,
        Integer withdrawalPeriodDays,
        LocalDate nextDueDate,
        boolean reminderAcknowledged,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        /** First day the animal or group is clear of this treatment's withdrawal period; null when it has none. */
        LocalDate withdrawalClearOn,
        Integer milkWithdrawalDays,
        Integer eggWithdrawalDays,
        /** First day milk may be sold again after this treatment; null when no milk period was given. */
        LocalDate milkClearOn,
        /** First day eggs may be sold again after this treatment; null when no egg period was given. */
        LocalDate eggClearOn
) {
    /** With the clear-on date but without milk and egg periods. */
    public HealthEventResponse(UUID id, UUID animalId, UUID groupId, String eventType, LocalDate eventDate, String description,
                               String productUsed, String dosage, UUID administeredBy, String administeredByName,
                               String veterinarian, BigDecimal cost, Integer withdrawalPeriodDays, LocalDate nextDueDate,
                               boolean reminderAcknowledged, String status, String notes, Instant createdAt, Instant updatedAt,
                               LocalDate withdrawalClearOn) {
        this(id, animalId, groupId, eventType, eventDate, description, productUsed, dosage, administeredBy, administeredByName,
                veterinarian, cost, withdrawalPeriodDays, nextDueDate, reminderAcknowledged, status, notes, createdAt, updatedAt,
                withdrawalClearOn, null, null, null, null);
    }

    /** From the stored event, with the milk and egg periods and the days they clear. */
    public HealthEventResponse(UUID id, UUID animalId, UUID groupId, String eventType, LocalDate eventDate, String description,
                               String productUsed, String dosage, UUID administeredBy, String administeredByName,
                               String veterinarian, BigDecimal cost, Integer withdrawalPeriodDays, LocalDate nextDueDate,
                               boolean reminderAcknowledged, String status, String notes, Instant createdAt, Instant updatedAt,
                               Integer milkWithdrawalDays, Integer eggWithdrawalDays) {
        this(id, animalId, groupId, eventType, eventDate, description, productUsed, dosage, administeredBy, administeredByName,
                veterinarian, cost, withdrawalPeriodDays, nextDueDate, reminderAcknowledged, status, notes, createdAt, updatedAt,
                za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.clearOn(eventDate, withdrawalPeriodDays),
                milkWithdrawalDays, eggWithdrawalDays,
                za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.clearOn(eventDate, milkWithdrawalDays),
                za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.clearOn(eventDate, eggWithdrawalDays));
    }

    public HealthEventResponse(UUID id, UUID animalId, UUID groupId, String eventType, LocalDate eventDate, String description,
                               String productUsed, String dosage, UUID administeredBy, String administeredByName,
                               String veterinarian, BigDecimal cost, Integer withdrawalPeriodDays, LocalDate nextDueDate,
                               boolean reminderAcknowledged, String status, String notes, Instant createdAt, Instant updatedAt) {
        this(id, animalId, groupId, eventType, eventDate, description, productUsed, dosage, administeredBy, administeredByName,
                veterinarian, cost, withdrawalPeriodDays, nextDueDate, reminderAcknowledged, status, notes, createdAt, updatedAt,
                za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.clearOn(eventDate, withdrawalPeriodDays));
    }
}
