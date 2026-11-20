package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.legalcompliance.application.internal.FeedRules.Item;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeedRulesTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static final String RSS = """
        <?xml version="1.0"?><rss version="2.0"><channel><title>Gazette</title>
          <item><title>Draft &lt;b&gt;Data&lt;/b&gt; Regulations</title><link>https://example.gov.za/a</link><guid>g-1</guid><pubDate>Tue, 10 Nov 2026 08:00:00 GMT</pubDate>
            <description>&lt;p&gt;Comment &amp;amp; input&lt;/p&gt;</description></item>
          <item><title>No guid item</title><link>https://example.gov.za/b</link></item>
          <item><title></title><guid>blank</guid></item>
        </channel></rss>""";

    @Test @DisplayName("RSS: titles and summaries are stripped to text, dates parsed, a missing guid falls back to the link, blank titles skipped")
    void rss() {
        List<Item> items = FeedRules.parse(b(RSS));
        assertEquals(2, items.size());
        assertEquals("g-1", items.get(0).guid());
        assertEquals("Draft Data Regulations", items.get(0).title());
        assertEquals("Comment & input", items.get(0).summary());
        assertEquals(LocalDate.of(2026, 11, 10), items.get(0).publishedOn());
        assertEquals("https://example.gov.za/b", items.get(1).guid());
        assertNull(items.get(1).summary());
        assertNull(items.get(1).publishedOn());
    }

    @Test @DisplayName("Atom: the alternate link is used and the ISO date parsed")
    void atom() {
        String atom = """
            <feed xmlns="http://www.w3.org/2005/Atom"><entry><id>urn:1</id><title>Notice</title>
              <link rel="self" href="https://x.example/self"/><link rel="alternate" href="https://x.example/alt"/>
              <updated>2026-11-01T10:00:00+02:00</updated><summary>Short</summary></entry></feed>""";
        List<Item> items = FeedRules.parse(b(atom));
        assertEquals(1, items.size());
        assertEquals("urn:1", items.get(0).guid());
        assertEquals("https://x.example/alt", items.get(0).link());
        assertEquals(LocalDate.of(2026, 11, 1), items.get(0).publishedOn());
    }

    @Test @DisplayName("A DOCTYPE (and so any external entity) is refused outright")
    void doctypeRefused() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><item><title>&x;</title></item></channel></rss>";
        assertThrows(IllegalArgumentException.class, () -> FeedRules.parse(b(xxe)));
    }

    @Test @DisplayName("Only http(s) links survive; scripts and data URLs are dropped")
    void links() {
        String x = "<rss><channel><item><title>A</title><link>javascript:alert(1)</link><guid>1</guid></item><item><title>B</title><link>http://ok.example/x</link><guid>2</guid></item></channel></rss>";
        List<Item> items = FeedRules.parse(b(x));
        assertNull(items.get(0).link());
        assertEquals("http://ok.example/x", items.get(1).link());
    }

    @Test @DisplayName("Duplicate guids collapse, and no more than the item cap is read")
    void dedupeAndCap() {
        StringBuilder sb = new StringBuilder("<rss><channel>");
        sb.append("<item><title>Same</title><guid>dup</guid></item><item><title>Same again</title><guid>dup</guid></item>");
        for (int i = 0; i < 150; i++) sb.append("<item><title>T").append(i).append("</title><guid>g").append(i).append("</guid></item>");
        sb.append("</channel></rss>");
        List<Item> items = FeedRules.parse(b(sb.toString()));
        assertEquals(FeedRules.MAX_ITEMS, items.size());
        assertEquals("Same", items.get(0).title());
    }

    @Test @DisplayName("Overlong text is clipped to the column sizes")
    void clipped() {
        String x = "<rss><channel><item><title>" + "t".repeat(400) + "</title><description>" + "d".repeat(5000) + "</description><guid>1</guid></item></channel></rss>";
        Item i = FeedRules.parse(b(x)).get(0);
        assertEquals(FeedRules.MAX_TITLE, i.title().length());
        assertEquals(FeedRules.MAX_SUMMARY, i.summary().length());
    }

    @Test @DisplayName("Empty, oversized, malformed and non-feed XML are all rejected with a plain message")
    void rejected() {
        assertThrows(IllegalArgumentException.class, () -> FeedRules.parse(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> FeedRules.parse(new byte[FeedRules.MAX_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> FeedRules.parse(b("<rss><channel>")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> FeedRules.parse(b("<html><body>hello</body></html>")));
        assertEquals("The feed could not be read as RSS or Atom", e.getMessage());
    }
}
