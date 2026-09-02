package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record CreatePlacementBatchRequest(
        String batchReference, LocalDate placedDate, @NotEmpty @Valid List<DebtorPlacementLineRequest> lines,
        String notes
) {}
