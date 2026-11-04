package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain rules for the address on a site: a South African postal code is four digits; the province is one of the nine. */
public final class FmSiteAddressRules {

    public static final List<String> PROVINCES = List.of("Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal",
            "Limpopo", "Mpumalanga", "North West", "Northern Cape", "Western Cape");
    private static final Map<String, String> CODES = Map.of("EC", "Eastern Cape", "FS", "Free State", "GP", "Gauteng",
            "KZN", "KwaZulu-Natal", "LP", "Limpopo", "MP", "Mpumalanga", "NW", "North West", "NC", "Northern Cape", "WC", "Western Cape");

    private FmSiteAddressRules() {}

    /** Returns the address with the province in its proper spelling and the postal code trimmed; throws IllegalArgumentException with a plain message. */
    public static Map<String, String> check(Map<String, String> address) {
        if (address == null) return null;
        Map<String, String> out = new LinkedHashMap<>(address);
        String postal = trim(out.get("postalCode"));
        if (postal != null) {
            if (!postal.matches("\\d{4}")) throw new IllegalArgumentException("The postal code must be four digits, like 2196.");
            out.put("postalCode", postal);
        } else out.remove("postalCode");
        String province = trim(out.get("province"));
        if (province != null) {
            String fixed = null;
            for (String p : PROVINCES) if (p.equalsIgnoreCase(province)) fixed = p;
            if (fixed == null) fixed = CODES.get(province.toUpperCase());
            if (fixed == null) throw new IllegalArgumentException("Choose one of the nine provinces, for example Gauteng or Western Cape.");
            out.put("province", fixed);
        } else out.remove("province");
        return out;
    }

    private static String trim(String s) { return s == null || s.isBlank() ? null : s.strip(); }
}
