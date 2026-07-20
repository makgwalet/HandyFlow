package za.co.handyflow.platform.accountant.dto;

import java.util.UUID;

public record CoaAccountResponse(
        UUID id,
        String accountCode,
        String accountName,
        String accountType,
        String subType,
        boolean vatApplicable,
        String vatType,
        boolean active
) {
}