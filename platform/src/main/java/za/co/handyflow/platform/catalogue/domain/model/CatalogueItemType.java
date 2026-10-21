package za.co.handyflow.platform.catalogue.domain.model;

/** What kind of offering a catalogue item is. A product is stocked; the others are not. */
public enum CatalogueItemType {
    PRODUCT, SERVICE, LABOUR, SUBSCRIPTION, PACKAGE, RENTAL, CONSUMABLE, OTHER
}
