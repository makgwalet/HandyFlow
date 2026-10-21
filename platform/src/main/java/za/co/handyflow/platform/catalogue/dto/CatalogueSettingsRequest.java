package za.co.handyflow.platform.catalogue.dto;

/** Either or both; a setting left out is not changed. */
public record CatalogueSettingsRequest(Boolean priceApprovalRequired, Boolean itemApprovalRequired) {
    public CatalogueSettingsRequest(Boolean priceApprovalRequired) { this(priceApprovalRequired, null); }
}
