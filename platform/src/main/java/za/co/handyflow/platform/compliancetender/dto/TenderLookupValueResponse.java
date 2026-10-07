package za.co.handyflow.platform.compliancetender.dto;

import java.util.UUID;

/** A value the company added to a pick-list; {@code listKey} says which one. */
public record TenderLookupValueResponse(UUID id, String listKey, String value) {}
