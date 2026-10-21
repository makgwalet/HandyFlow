package za.co.handyflow.platform.catalogue.dto;

/** Any field left out is unchanged. {@code primary=true} makes it the main picture. */
public record MediaUpdateRequest(String title, String altText, Boolean primary) {}
