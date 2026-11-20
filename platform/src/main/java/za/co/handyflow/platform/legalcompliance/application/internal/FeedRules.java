package za.co.handyflow.platform.legalcompliance.application.internal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns RSS 2.0 or Atom text into plain items. Defensive by design: the feed is untrusted input, so DTDs and external entities are refused outright, size and item counts
 * are capped, markup is stripped to text, and only http(s) links are kept. No network access here; the text is handed in.
 */
public final class FeedRules {

    private FeedRules() {}

    public static final int MAX_BYTES = 1_048_576, MAX_ITEMS = 100, MAX_TITLE = 255, MAX_SUMMARY = 2000, MAX_LINK = 500, MAX_GUID = 500;

    public record Item(String guid, String title, String link, String summary, LocalDate publishedOn) {}

    public static List<Item> parse(byte[] xml) {
        if (xml == null || xml.length == 0) throw new IllegalArgumentException("The feed was empty");
        if (xml.length > MAX_BYTES) throw new IllegalArgumentException("The feed is larger than " + (MAX_BYTES / 1024) + " KB");
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalArgumentException("The feed could not be read as RSS or Atom");
        }
        Element root = doc.getDocumentElement();
        String rootName = local(root);
        boolean atom = rootName.equals("feed");
        if (!atom && !rootName.equals("rss") && !rootName.equals("RDF")) throw new IllegalArgumentException("The feed could not be read as RSS or Atom");

        Map<String, Item> byGuid = new LinkedHashMap<>();
        NodeList all = doc.getElementsByTagNameNS("*", atom ? "entry" : "item");
        for (int i = 0; i < all.getLength() && byGuid.size() < MAX_ITEMS; i++) {
            Element e = (Element) all.item(i);
            String title = clip(text(child(e, "title")), MAX_TITLE);
            if (title.isEmpty()) continue;
            String link = atom ? atomLink(e) : text(child(e, "link"));
            link = safeLink(link);
            String summary = clip(text(first(e, "description", "summary", "content", "encoded")), MAX_SUMMARY);
            LocalDate date = date(text(first(e, "pubDate", "published", "updated", "date")));
            String guid = clip(text(first(e, "guid", "id")), MAX_GUID);
            if (guid.isEmpty()) guid = link != null ? link : clip(title + "|" + (date == null ? "" : date), MAX_GUID);
            byGuid.putIfAbsent(guid, new Item(guid, title, link, summary.isEmpty() ? null : summary, date));
        }
        return new ArrayList<>(byGuid.values());
    }


    /** Markup removed, a handful of entities decoded, whitespace collapsed. */
    public static String plain(String s) {
        if (s == null) return "";
        String t = s.replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        return t.replaceAll("\\s+", " ").trim();
    }

    static LocalDate date(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        try { return ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate(); } catch (Exception ignored) { }
        try { return OffsetDateTime.parse(v).toLocalDate(); } catch (Exception ignored) { }
        try { return LocalDate.parse(v.length() >= 10 ? v.substring(0, 10) : v); } catch (Exception ignored) { }
        return null;
    }

    private static String safeLink(String l) {
        if (l == null) return null;
        String s = l.trim();
        if (s.length() > MAX_LINK || !(s.regionMatches(true, 0, "https://", 0, 8) || s.regionMatches(true, 0, "http://", 0, 7))) return null;
        return s;
    }

    private static String atomLink(Element e) {
        String fallback = null;
        NodeList links = e.getElementsByTagNameNS("*", "link");
        for (int i = 0; i < links.getLength(); i++) {
            Element l = (Element) links.item(i);
            if (l.getParentNode() != e) continue;
            String rel = l.getAttribute("rel"), href = l.getAttribute("href");
            if (href.isBlank()) continue;
            if (rel.isBlank() || rel.equals("alternate")) return href;
            if (fallback == null) fallback = href;
        }
        return fallback;
    }

    private static Element first(Element e, String... names) {
        for (String n : names) { Element c = child(e, n); if (c != null) return c; }
        return null;
    }

    private static Element child(Element e, String name) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n.getNodeType() == Node.ELEMENT_NODE && local((Element) n).equals(name)) return (Element) n;
        return null;
    }

    private static String text(Element e) { return e == null ? "" : plain(e.getTextContent()); }

    private static String local(Element e) { return e.getLocalName() != null ? e.getLocalName() : e.getTagName(); }

    private static String clip(String s, int max) { return s == null ? "" : s.length() > max ? s.substring(0, max) : s; }
}
