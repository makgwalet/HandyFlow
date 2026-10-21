package za.co.handyflow.platform.crm.dto;

import java.time.LocalDate;

public record CampaignRequest(String name, String goal, LocalDate startsOn, LocalDate endsOn) {}
