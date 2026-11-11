package za.co.handyflow.platform.warehousing.application.internal;

import java.util.Arrays;
import java.util.Locale;

/**
 * The warehouse's operating model and industry profile. They are separate on purpose: the model says whose stock is held, the profile says what
 * tracking new items start with. Neither changes how storage is billed or what the subscription allows; those are their own settings.
 */
public final class WhseSettingsRules {

    private WhseSettingsRules() {}

    public enum OperatingModel {
        INTERNAL("Internal: your own stock only"), THREE_PL("3PL: stock of several customers"), HYBRID("Hybrid: your own stock and customers'");
        public final String label;
        OperatingModel(String label) { this.label = label; }
    }

    public enum IndustryProfile {
        GENERAL("General", false, false), PHARMA("Pharmaceuticals and medical supplies", true, true),
        FOOD("Food and beverages", true, true), AGRI("Agriculture inputs", true, true);
        public final String label;
        public final boolean lotTracked, expiryTracked;
        IndustryProfile(String label, boolean lotTracked, boolean expiryTracked) { this.label = label; this.lotTracked = lotTracked; this.expiryTracked = expiryTracked; }
    }

    /** What a new item starts with. Always editable per item afterwards. */
    public record ItemDefaults(boolean lotTracked, boolean expiryTracked) {}

    public static OperatingModel parseModel(String v) {
        try { return OperatingModel.valueOf(v == null ? "" : v.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose an operating model: " + Arrays.toString(OperatingModel.values()) + "."); }
    }

    public static IndustryProfile parseIndustry(String v) {
        try { return IndustryProfile.valueOf(v == null ? "" : v.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose an industry profile: " + Arrays.toString(IndustryProfile.values()) + "."); }
    }

    public static ItemDefaults itemDefaults(IndustryProfile p) { return new ItemDefaults(p.lotTracked, p.expiryTracked); }

    /** Moving to INTERNAL is refused while more than one customer account is open: close or merge them first. Other changes are always allowed. */
    public static void checkModelChange(OperatingModel to, int activeClients) {
        if (to == OperatingModel.INTERNAL && activeClients > 1) {
            throw new IllegalStateException("An internal warehouse holds one owner's stock, but " + activeClients + " client accounts are open. Close the extra accounts first.");
        }
    }

    /** An internal warehouse has one owner, so a second account is refused; 3PL and hybrid take as many as the package allows. */
    public static void checkNewClient(OperatingModel model, int activeClients) {
        if (model == OperatingModel.INTERNAL && activeClients >= 1) {
            throw new IllegalStateException("An internal warehouse holds one owner's stock. To store other customers' stock, change the operating model to 3PL or hybrid.");
        }
    }
}
