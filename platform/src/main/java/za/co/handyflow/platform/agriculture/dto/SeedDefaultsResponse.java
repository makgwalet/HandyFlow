package za.co.handyflow.platform.agriculture.dto;

/** How many standard entries were added, and how many were left alone because the tenant already had one with that name. */
public record SeedDefaultsResponse(int added, int alreadyThere) {}
