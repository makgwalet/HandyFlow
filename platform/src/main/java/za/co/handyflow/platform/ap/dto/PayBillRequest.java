package za.co.handyflow.platform.ap.dto;

import java.util.UUID;

public record PayBillRequest(
        String paymentRef,
        UUID   bankAccountId
) {}
