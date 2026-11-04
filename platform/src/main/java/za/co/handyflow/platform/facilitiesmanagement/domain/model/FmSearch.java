package za.co.handyflow.platform.facilitiesmanagement.domain.model;

/** Turns what someone typed into a safe, case-insensitive "contains" pattern for a LIKE query. */
public final class FmSearch {

    public static final int MAX_LENGTH = 100;

    private FmSearch() {}

    /**
     * "%text%" in lower case, with %, _ and the escape character taken literally (escape is a backslash).
     * Blank or null gives "%", which matches everything.
     */
    public static String likePattern(String typed) {
        if (typed == null) return "%";
        String t = typed.strip().toLowerCase();
        if (t.isEmpty()) return "%";
        if (t.length() > MAX_LENGTH) t = t.substring(0, MAX_LENGTH);
        StringBuilder sb = new StringBuilder("%");
        for (char c : t.toCharArray()) {
            if (c == '\\' || c == '%' || c == '_') sb.append('\\');
            sb.append(c);
        }
        return sb.append('%').toString();
    }
}
