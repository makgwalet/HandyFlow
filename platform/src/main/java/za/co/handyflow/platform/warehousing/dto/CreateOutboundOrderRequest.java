package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public record CreateOutboundOrderRequest(
        String orderReference, String shipToName, String shipToAddress, LocalDate requestedShipDate,
        @NotEmpty @Valid List<OutboundLineRequest> lines, String notes
) {}
