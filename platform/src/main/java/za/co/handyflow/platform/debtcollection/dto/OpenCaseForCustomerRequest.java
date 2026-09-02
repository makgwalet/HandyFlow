package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record OpenCaseForCustomerRequest(
        @NotNull UUID customerId,
        LocalDate openedDate,
        UUID assignedToUserId,
        String assignedToUserName,
        String notes
) {}
