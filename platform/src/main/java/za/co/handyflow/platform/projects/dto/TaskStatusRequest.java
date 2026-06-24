package za.co.handyflow.platform.projects.dto;

import java.math.BigDecimal;

public record TaskStatusRequest(
        String     action,       // START|COMPLETE|PROGRESS|BLOCK|CANCEL
        BigDecimal progressPct   // only for PROGRESS action
) {}
