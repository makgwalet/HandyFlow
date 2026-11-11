package za.co.handyflow.platform.warehousing.application.internal;

import java.util.Locale;
import java.util.regex.Pattern;

/** Codes and names of warehouses. The code is short, upper case and stays the same once chosen. */
public final class WhseWarehouseRules {

    private WhseWarehouseRules() {}

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,19}$");
    public static final int NAME_MAX = 120;

    /** The code in its stored form (upper case, trimmed), or an error saying what is allowed. */
    public static String code(String raw) {
        String c = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(c).matches()) throw new IllegalArgumentException("The code is 1 to 20 letters, digits, dashes or underscores, starting with a letter or digit (for example CPT or JHB-2).");
        return c;
    }

    public static String name(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("Name the warehouse.");
        if (n.length() > NAME_MAX) throw new IllegalArgumentException("The name can be at most " + NAME_MAX + " characters.");
        return n;
    }

    /** The default warehouse cannot be switched off or deleted; make another one the default first. A warehouse that holds stock cannot be switched off either. */
    public static void checkDeactivate(boolean isDefault, java.math.BigDecimal stockOnHand) {
        if (isDefault) throw new IllegalStateException("This is the default warehouse. Make another warehouse the default first.");
        if (stockOnHand != null && stockOnHand.signum() > 0) throw new IllegalStateException("This warehouse still holds stock. Move it to another warehouse first.");
    }
}
