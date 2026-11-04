package za.co.handyflow.platform.crm.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what a person typed into the search box into a Postgres prefix query, so "Ka" finds "Karoo".
 * Only letters and digits are kept, so nothing typed can break the query.
 */
public final class CustomerSearchTerms {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    /** More words than this are ignored; nobody needs 20 words to find a customer. */
    public static final int MAX_WORDS = 8;

    private CustomerSearchTerms() {}

    /** "Ka Fresh" becomes "ka:* & fresh:*"; null when there is nothing searchable. */
    public static String prefixQuery(String typed) {
        if (typed == null) return null;
        List<String> words = new ArrayList<>();
        Matcher m = WORD.matcher(typed.toLowerCase());
        while (m.find() && words.size() < MAX_WORDS) words.add(m.group() + ":*");
        return words.isEmpty() ? null : String.join(" & ", words);
    }
}
