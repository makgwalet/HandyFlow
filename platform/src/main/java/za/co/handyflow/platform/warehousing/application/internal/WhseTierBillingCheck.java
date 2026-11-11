package za.co.handyflow.platform.warehousing.application.internal;

import java.math.BigDecimal;

/**
 * Compares a tenant's Warehousing tier with what the platform bills for the module. The platform still bills the flat catalogue price (one price per
 * module, no tiers) until the commercial entitlement work lands, so a tier priced differently from the catalogue is flagged for a person to handle.
 * Pure: no database.
 */
public final class WhseTierBillingCheck {
    private WhseTierBillingCheck() {}

    public enum Finding {
        /** The module is subscribed and no tier is assigned: unrestricted, billed at the catalogue price. */
        NO_TIER("No tier assigned: billed at the catalogue price, no limits."),
        /** The tier's price equals the catalogue price. */
        MATCHES("The tier price matches what is billed."),
        /** The tier costs more or less than the catalogue price, which is what the platform actually bills. */
        PRICE_DIFFERS("The tier price differs from the catalogue price, which is what is billed. Adjust the invoice or discount by hand."),
        /** Enterprise: priced by quotation, not by the catalogue. */
        CUSTOM_QUOTE("Priced by quotation: bill the agreed amount by hand."),
        /** A tier is assigned but the tenant has no active Warehousing subscription. */
        TIER_WITHOUT_MODULE("A tier is assigned but Warehousing is not an active subscription for this tenant.");
        public final String message;
        Finding(String message) { this.message = message; }
    }

    public static Finding classify(boolean moduleActive, boolean tierAssigned, BigDecimal tierPrice, BigDecimal cataloguePrice) {
        if (!moduleActive) return tierAssigned ? Finding.TIER_WITHOUT_MODULE : null; // nothing to report for a tenant with neither
        if (!tierAssigned) return Finding.NO_TIER;
        if (tierPrice == null) return Finding.CUSTOM_QUOTE;
        if (cataloguePrice != null && tierPrice.compareTo(cataloguePrice) == 0) return Finding.MATCHES;
        return Finding.PRICE_DIFFERS;
    }
}
