package za.co.handyflow.platform.catalogue.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a catalogue item. Only ACTIVE items can be picked for new quotes, invoices and sales;
 * historical documents keep reading items in any status.
 */
public enum CatalogueItemStatus {
    DRAFT, ACTIVE, SUSPENDED, DISCONTINUED, ARCHIVED;

    /** The states this one may move to. */
    public Set<CatalogueItemStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(ACTIVE, ARCHIVED);
            case ACTIVE -> EnumSet.of(SUSPENDED, DISCONTINUED, ARCHIVED);
            case SUSPENDED -> EnumSet.of(ACTIVE, DISCONTINUED, ARCHIVED);
            case DISCONTINUED -> EnumSet.of(ACTIVE, ARCHIVED);
            case ARCHIVED -> EnumSet.of(DRAFT);
        };
    }

    public boolean canMoveTo(CatalogueItemStatus target) { return next().contains(target); }

    /** Moving into these states needs a written reason. */
    public boolean needsReason() { return this == SUSPENDED || this == DISCONTINUED || this == ARCHIVED; }

    /** Moving into these states needs the archive permission. */
    public boolean needsArchivePermission() { return this == DISCONTINUED || this == ARCHIVED; }

    public boolean sellable() { return this == ACTIVE; }
}
