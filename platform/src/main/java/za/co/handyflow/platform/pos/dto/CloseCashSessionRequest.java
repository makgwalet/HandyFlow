package za.co.handyflow.platform.pos.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// POST /pos/cash-sessions/{id}/close
public record CloseCashSessionRequest(
        @NotNull BigDecimal closingFloat,   // physical cash counted in drawer
        String              notes
) {}
