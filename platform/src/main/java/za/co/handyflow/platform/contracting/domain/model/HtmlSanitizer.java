package za.co.handyflow.platform.contracting.domain.model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Allow-list sanitiser for contract and template HTML.
 *
 * Contract bodies are rendered as HTML in the staff screens and on the public signing page, so anything
 * stored must not be able to run script. Only the formatting the seeded templates use (plus tables, which
 * the contract PDF and amendments need) is kept. No attributes except table spans, no links, no images,
 * no inline styles. {{variable}} placeholders are plain text and pass through unchanged.
 */
public final class HtmlSanitizer {

    private static final Safelist ALLOWED = new Safelist()
            .addTags("p", "div", "span", "br", "hr", "h1", "h2", "h3", "h4", "h5", "h6",
                    "strong", "b", "em", "i", "u", "sub", "sup", "blockquote",
                    "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan");

    private HtmlSanitizer() {}

    /** Returns sanitised HTML; null stays null. */
    public static String clean(String html) {
        if (html == null) return null;
        return Jsoup.clean(html, "", ALLOWED, new Document.OutputSettings().prettyPrint(false));
    }
}
