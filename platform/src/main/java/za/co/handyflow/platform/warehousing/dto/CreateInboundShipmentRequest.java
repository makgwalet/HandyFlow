package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record CreateInboundShipmentRequest(
        String referenceNumber, LocalDate expectedDate, @NotEmpty @Valid List<InboundLineRequest> lines, String notes
) {}
