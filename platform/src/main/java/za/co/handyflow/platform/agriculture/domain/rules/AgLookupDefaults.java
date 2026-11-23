package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The standard species and crop types offered to a new tenant (South African farming), and the name rules for these lists.
 * Pure. Nothing is added unasked: a farm clicks "Add standard list" and entries it already has are left alone.
 */
public final class AgLookupDefaults {

    private AgLookupDefaults() {}

    public record SpeciesDefault(String name, String category, String unit, String trackingMode, Integer gestationDays, BigDecimal maturityWeightKg) {}

    public record CropTypeDefault(String name, String category, Integer typicalGrowingDays, String unit) {}

    private static SpeciesDefault sp(String name, String category, String mode, Integer gestation) {
        return new SpeciesDefault(name, category, "head", mode, gestation, null);
    }

    private static CropTypeDefault ct(String name, String category, Integer days, String unit) {
        return new CropTypeDefault(name, category, days, unit);
    }

    public static final List<SpeciesDefault> SPECIES = List.of(
            sp("Cattle", "LIVESTOCK", "BOTH", 283),
            sp("Sheep", "LIVESTOCK", "BOTH", 150),
            sp("Goats", "LIVESTOCK", "BOTH", 150),
            sp("Pigs", "LIVESTOCK", "BOTH", 114),
            sp("Horses", "LIVESTOCK", "INDIVIDUAL", 340),
            sp("Game", "LIVESTOCK", "GROUP", null),
            sp("Broilers", "POULTRY", "GROUP", null),
            sp("Layers", "POULTRY", "GROUP", null),
            sp("Ducks", "POULTRY", "GROUP", null),
            sp("Turkeys", "POULTRY", "GROUP", null),
            sp("Ostriches", "POULTRY", "GROUP", null),
            sp("Tilapia", "AQUACULTURE", "GROUP", null),
            sp("Trout", "AQUACULTURE", "GROUP", null));

    public static final List<CropTypeDefault> CROP_TYPES = List.of(
            ct("Maize (white)", "CEREAL", 150, "t"), ct("Maize (yellow)", "CEREAL", 150, "t"), ct("Wheat", "CEREAL", 150, "t"),
            ct("Sorghum", "CEREAL", 130, "t"), ct("Barley", "CEREAL", 130, "t"), ct("Oats", "CEREAL", 140, "t"),
            ct("Soybeans", "OILSEED", 130, "t"), ct("Sunflower", "OILSEED", 120, "t"), ct("Canola", "OILSEED", 150, "t"),
            ct("Groundnuts", "OILSEED", 140, "t"),
            ct("Dry beans", "LEGUME", 100, "t"), ct("Cowpeas", "LEGUME", 90, "t"),
            ct("Potatoes", "VEGETABLE", 110, "t"), ct("Tomatoes", "VEGETABLE", 120, "t"), ct("Onions", "VEGETABLE", 150, "t"),
            ct("Cabbage", "VEGETABLE", 90, "t"), ct("Butternut", "VEGETABLE", 120, "t"), ct("Carrots", "VEGETABLE", 100, "t"),
            ct("Citrus", "FRUIT", null, "t"), ct("Avocados", "FRUIT", null, "t"), ct("Macadamia", "FRUIT", null, "t"),
            ct("Pecans", "FRUIT", null, "t"), ct("Table grapes", "FRUIT", null, "t"), ct("Apples", "FRUIT", null, "t"),
            ct("Lucerne", "FODDER", null, "bales"), ct("Eragrostis (teff)", "FODDER", 90, "bales"), ct("Silage maize", "FODDER", 120, "t"),
            ct("Sugarcane", "OTHER", 365, "t"), ct("Cotton", "OTHER", 180, "t"));

    /** Case, spacing and edge whitespace folded, so "cattle", " Cattle " and "CATTLE" are the same name. */
    public static String normalise(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** A name as it will be stored: trimmed, inner whitespace collapsed. */
    public static String clean(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    /** {@code what} is e.g. "species". Refuses a name the tenant already has, ignoring case and spacing. */
    public static void requireNewName(Iterable<String> existingNames, String name, String what) {
        String wanted = normalise(name);
        for (String e : existingNames) {
            if (normalise(e).equals(wanted)) throw new IllegalArgumentException("A " + what + " named '" + clean(e) + "' already exists");
        }
    }

    public static List<SpeciesDefault> missingSpecies(Iterable<String> existingNames) {
        Set<String> have = normalised(existingNames);
        List<SpeciesDefault> out = new ArrayList<>();
        for (SpeciesDefault d : SPECIES) if (!have.contains(normalise(d.name()))) out.add(d);
        return out;
    }

    public static List<CropTypeDefault> missingCropTypes(Iterable<String> existingNames) {
        Set<String> have = normalised(existingNames);
        List<CropTypeDefault> out = new ArrayList<>();
        for (CropTypeDefault d : CROP_TYPES) if (!have.contains(normalise(d.name()))) out.add(d);
        return out;
    }

    private static Set<String> normalised(Iterable<String> names) {
        Set<String> out = new HashSet<>();
        for (String n : names) out.add(normalise(n));
        return out;
    }
}
