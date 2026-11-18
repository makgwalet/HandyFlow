package za.co.handyflow.platform.contracting.domain.model;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Block-level comparison of two contract bodies. A body is split into its top-level blocks (paragraphs, headings, lists, tables);
 * blocks are matched with a longest-common-subsequence, and each run of removed and added blocks between matches is one
 * <em>hunk</em> that a reviewer can accept or reject as a whole. Accepting a hunk takes the proposed blocks, rejecting it keeps
 * the current ones. Comparison is by block, not by word: a changed word shows its whole paragraph as removed and added.
 */
public final class BodyDiff {

    public static final String EQUAL = "EQUAL";
    public static final String HUNK = "HUNK";
    private static final int MAX_BLOCKS = 3000;

    /** EQUAL: {@code blocks} are unchanged. HUNK: {@code removed} are in the current text, {@code added} in the proposal. */
    public record Segment(String type, int hunkId, List<String> blocks, List<String> removed, List<String> added) {}

    private BodyDiff() {}

    public static List<String> blocks(String html) {
        List<String> out = new ArrayList<>();
        if (html == null || html.isBlank()) return out;
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (Node n : doc.body().childNodes()) {
            if (n instanceof Element e) out.add(e.outerHtml());
            else if (n instanceof TextNode t && !t.isBlank()) out.add(t.outerHtml().trim());
        }
        return out;
    }

    private static String key(String block) { return block.replaceAll("\\s+", " ").trim(); }

    public static List<Segment> diff(String currentHtml, String proposedHtml) {
        List<String> a = blocks(currentHtml), b = blocks(proposedHtml);
        if (a.size() > MAX_BLOCKS || b.size() > MAX_BLOCKS) throw new IllegalArgumentException("The contract is too large to compare");
        int n = a.size(), m = b.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                lcs[i][j] = key(a.get(i)).equals(key(b.get(j))) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);

        List<Segment> segments = new ArrayList<>();
        List<String> equal = new ArrayList<>(), removed = new ArrayList<>(), added = new ArrayList<>();
        int hunk = 0, i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && key(a.get(i)).equals(key(b.get(j)))) {
                if (!removed.isEmpty() || !added.isEmpty()) {
                    segments.add(new Segment(HUNK, hunk++, List.of(), List.copyOf(removed), List.copyOf(added)));
                    removed.clear(); added.clear();
                }
                equal.add(a.get(i)); i++; j++;
            } else {
                if (!equal.isEmpty()) { segments.add(new Segment(EQUAL, -1, List.copyOf(equal), List.of(), List.of())); equal.clear(); }
                if (j >= m || (i < n && lcs[i + 1][j] >= lcs[i][j + 1])) removed.add(a.get(i++));
                else added.add(b.get(j++));
            }
        }
        if (!equal.isEmpty()) segments.add(new Segment(EQUAL, -1, List.copyOf(equal), List.of(), List.of()));
        if (!removed.isEmpty() || !added.isEmpty()) segments.add(new Segment(HUNK, hunk, List.of(), List.copyOf(removed), List.copyOf(added)));
        return segments;
    }

    public static int hunkCount(List<Segment> segments) {
        return (int) segments.stream().filter(s -> HUNK.equals(s.type())).count();
    }

    /** The current text with the accepted hunks taken from the proposal and every other hunk left as it is now. */
    public static String merge(String currentHtml, String proposedHtml, Set<Integer> accepted) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : diff(currentHtml, proposedHtml)) {
            if (EQUAL.equals(s.type())) s.blocks().forEach(sb::append);
            else (accepted.contains(s.hunkId()) ? s.added() : s.removed()).forEach(sb::append);
        }
        return sb.toString();
    }
}
