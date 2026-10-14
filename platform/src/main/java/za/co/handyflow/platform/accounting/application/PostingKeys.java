package za.co.handyflow.platform.accounting.application;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Keys that identify the business event behind a journal, so the same event never posts twice (finance patch F3,
 * 2026-10-14). A key is {@code module:event:id}, for example {@code invoicing:invoice-issued:<invoice id>}. Pure.
 * <p>
 * Use the id of the thing that happens once: the invoice for "issued", the bill for "paid in full", the payment record or
 * the event's own id for "payment received" (an invoice can take several payments, so the invoice id is not enough).
 */
public final class PostingKeys {

    public static final int MAX_LENGTH = 200;
    private static final Pattern PART = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private PostingKeys() {}

    public static String of(String module, String event, Object id) {
        if (module == null || !PART.matcher(module).matches())
            throw new IllegalArgumentException("Posting key module must be lower-case words joined by '-': " + module);
        if (event == null || !PART.matcher(event).matches())
            throw new IllegalArgumentException("Posting key event must be lower-case words joined by '-': " + event);
        String idPart = Objects.requireNonNull(id, "Posting key needs the id of the business event").toString().trim();
        if (idPart.isEmpty()) throw new IllegalArgumentException("Posting key needs the id of the business event");
        return check(module + ":" + event + ":" + idPart);
    }

    /** The key itself when usable, otherwise an IllegalArgumentException. */
    public static String check(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("A posting key is required");
        if (key.length() > MAX_LENGTH)
            throw new IllegalArgumentException("A posting key is at most " + MAX_LENGTH + " characters");
        return key;
    }
}
