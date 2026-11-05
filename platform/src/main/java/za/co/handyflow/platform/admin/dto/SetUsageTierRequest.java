package za.co.handyflow.platform.admin.dto;

/** @param tier STARTER, GROWTH, BUSINESS or ENTERPRISE; null or blank removes the tier (no limits shown) */
public record SetUsageTierRequest(String tier, String reason) {}
