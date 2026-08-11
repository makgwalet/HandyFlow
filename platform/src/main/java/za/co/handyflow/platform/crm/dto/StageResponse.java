package za.co.handyflow.platform.crm.dto;

import java.util.UUID;

public record StageResponse(
        UUID   customerId,
        String customerType,
        String stage   // null for a CUSTOMER-type record — see Customer.changeStage
) {}