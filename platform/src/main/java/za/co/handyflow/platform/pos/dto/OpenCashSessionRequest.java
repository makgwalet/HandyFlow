// ─────────────────────────────────────────────────────────────────────────────
// Cash session DTOs
// ─────────────────────────────────────────────────────────────────────────────
package za.co.handyflow.platform.pos.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// POST /pos/cash-sessions/open
public record OpenCashSessionRequest(
        @NotNull BigDecimal openingFloat,
        String              notes
) {}
