package za.co.handyflow.platform.crm.dto;

import java.util.List;
import java.util.UUID;

/** band is HOT (60+), WARM (30-59) or COLD. */
public record LeadScoreResponse(UUID customerId, int score, String band, List<String> reasons) {}
