package za.co.handyflow.platform.agriculture.domain.rules;

import java.util.Locale;

/**
 * What the animal and group lists accept: a status filter, a search text and a page. Pure, so it is tested without Spring.
 * The search is a case-insensitive "contains" on tag/batch number, name and breed. The person's text is never
 * trusted as a pattern: % and _ are escaped, so searching "50%" finds "50%" and not everything.
 */
public final class AgListRules {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_QUERY = 60;
    /** The ESCAPE character in the repository queries. Not a backslash, which HQL and Java both treat specially. */
    public static final char ESCAPE = '!';

    private AgListRules() {}

    /** "" means every status. Upper-cased, so "active" works. */
    public static String status(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    /** The LIKE pattern for a search text: "%" (match all) when blank, otherwise "%text%" in lower case with wildcards escaped. */
    public static String likePattern(String query) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) return "%";
        if (q.length() > MAX_QUERY) throw new IllegalArgumentException("Search text can be at most " + MAX_QUERY + " characters");
        StringBuilder sb = new StringBuilder("%");
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (Character.isISOControl(c)) throw new IllegalArgumentException("Search text cannot contain control characters");
            if (c == ESCAPE || c == '%' || c == '_') sb.append(ESCAPE);
            sb.append(Character.toLowerCase(c));
        }
        return sb.append('%').toString();
    }

    public static int pageIndex(int requested) {
        return Math.max(0, requested);
    }

    /** At least 1 and at most 200; a missing or zero size gives the default. */
    public static int pageSize(int requested) {
        if (requested < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    /** 1-based first and last row shown, e.g. page 2 of size 50 with 120 rows is 51 to 100. Both 0 when empty. */
    public static int[] rowRange(int page, int size, long total) {
        if (total <= 0) return new int[] {0, 0};
        long first = (long) page * size + 1;
        if (first > total) return new int[] {0, 0};
        return new int[] {(int) first, (int) Math.min(total, first + size - 1)};
    }
}
