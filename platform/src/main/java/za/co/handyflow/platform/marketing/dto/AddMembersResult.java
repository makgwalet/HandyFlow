package za.co.handyflow.platform.marketing.dto;

/** notFound = addresses/ids that are not existing marketing contacts (they cannot be added). */
public record AddMembersResult(int added, int alreadyMembers, int notFound) {}