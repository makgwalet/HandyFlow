package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.util.Map;

public record UpdateFmSiteRequest(String name, String siteType, Map<String, String> address, String notes) {}
