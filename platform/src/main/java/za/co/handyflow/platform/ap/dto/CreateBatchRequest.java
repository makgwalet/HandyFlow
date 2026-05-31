package za.co.handyflow.platform.ap.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBatchRequest(
        @NotNull  UUID        bankAccountId,
                  String      description,
        @NotNull  LocalDate   paymentDate,
        @NotEmpty List<UUID>  billIds
) {}
