package za.co.handyflow.platform.crm.dto;

import java.time.LocalDate;
import java.util.UUID;
import za.co.handyflow.platform.crm.domain.model.CampaignStatus;

/** Rates are whole percentages of everyone added, or null while nobody has been added. */
public record CampaignResponse(UUID id, String name, String goal, CampaignStatus status, LocalDate startsOn, LocalDate endsOn, int steps,
                               int total, int contacted, int responded, int converted, Integer responseRate, Integer conversionRate) {}
