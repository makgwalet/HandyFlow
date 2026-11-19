package za.co.handyflow.platform.contracting.application.internal;

import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.ListItem;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.awt.Color;
import java.util.ArrayList;

/**
 * Turns the contract body (already sanitised HTML: paragraphs, headings, bold/italic/underline, sub/superscript, bulleted and numbered
 * lists, tables, quotes, rules) into PDF elements, so the PDF looks like what was written in the editor instead of a run of plain text.
 * Anything it does not know is rendered as its text. Nesting is capped so a hostile body cannot exhaust the stack.
 */
final class ContractPdfBody {

    private static final Color DARK = new Color(27, 58, 107);
    private static final Color TEAL = new Color(13, 148, 136);
    private static final Color LIGHT = new Color(248, 250, 252);
    private static final Color RULE = new Color(203, 213, 225);
    private static final float BODY_SIZE = 10f;
    private static final int MAX_DEPTH = 8;
    private static final int MAX_COLS = 12;

    private ContractPdfBody() {}

    /** One run of text with its look; a newline run is a line break. */
    private record Seg(String text, int style, float size, float rise, boolean newline) {
        Seg withText(String t) { return new Seg(t, style, size, rise, false); }
    }

    static java.util.List<Element> render(String html) {
        org.jsoup.nodes.Document parsed = Jsoup.parse(html == null ? "" : html);
        return blocks(parsed.body(), 0, Font.NORMAL);
    }

    // ── blocks ───────────────────────────────────────────────────────────────

    private static boolean isBlock(String tag) {
        return switch (tag) {
            case "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "blockquote", "hr" -> true;
            default -> false;
        };
    }

    private static java.util.List<Element> blocks(org.jsoup.nodes.Element container, int depth, int baseStyle) {
        java.util.List<Element> out = new ArrayList<>();
        java.util.List<Seg> buf = new ArrayList<>();
        if (depth > MAX_DEPTH) {
            collect(container, baseStyle, BODY_SIZE, 0f, buf);
            addParagraph(out, buf, Color.BLACK);
            return out;
        }
        for (Node n : container.childNodes()) {
            if (n instanceof org.jsoup.nodes.Element e && isBlock(e.tagName())) {
                addParagraph(out, buf, Color.BLACK);
                buf = new ArrayList<>();
                block(e, out, depth, baseStyle);
            } else {
                collect(n, baseStyle, BODY_SIZE, 0f, buf);
            }
        }
        addParagraph(out, buf, Color.BLACK);
        return out;
    }

    private static void block(org.jsoup.nodes.Element e, java.util.List<Element> out, int depth, int baseStyle) {
        String tag = e.tagName();
        switch (tag) {
            case "p", "div" -> out.addAll(blocks(e, depth + 1, baseStyle));
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                float size = switch (tag) { case "h1" -> 15f; case "h2" -> 13f; case "h3" -> 11.5f; default -> 10.5f; };
                java.util.List<Seg> buf = new ArrayList<>();
                for (Node c : e.childNodes()) collect(c, baseStyle | Font.BOLD, size, 0f, buf);
                Paragraph p = paragraph(buf, DARK);
                if (p != null) { p.setSpacingBefore(8); p.setSpacingAfter(4); out.add(p); }
            }
            case "ul", "ol" -> out.add(list(e, depth));
            case "table" -> { PdfPTable t = table(e, depth); if (t != null) out.add(t); }
            case "blockquote" -> out.add(quote(e, depth));
            case "hr" -> out.add(rule());
            default -> { }
        }
    }

    private static void addParagraph(java.util.List<Element> out, java.util.List<Seg> buf, Color color) {
        Paragraph p = paragraph(buf, color);
        if (p != null) { p.setSpacingAfter(6); out.add(p); }
    }

    // ── inline text ──────────────────────────────────────────────────────────

    private static void collect(Node n, int style, float size, float rise, java.util.List<Seg> buf) {
        if (n instanceof TextNode t) {
            String text = t.text();
            if (!text.isEmpty()) buf.add(new Seg(text, style, size, rise, false));
        } else if (n instanceof org.jsoup.nodes.Element e) {
            switch (e.tagName()) {
                case "br" -> { buf.add(new Seg("", style, size, rise, true)); return; }
                case "strong", "b" -> style |= Font.BOLD;
                case "em", "i" -> style |= Font.ITALIC;
                case "u" -> style |= Font.UNDERLINE;
                case "sub" -> { size = size * 0.75f; rise = -2f; }
                case "sup" -> { size = size * 0.75f; rise = 3f; }
                default -> { }
            }
            for (Node c : e.childNodes()) collect(c, style, size, rise, buf);
        }
    }

    private static java.util.List<Seg> trimmed(java.util.List<Seg> segs) {
        java.util.List<Seg> r = new ArrayList<>(segs);
        while (!r.isEmpty()) {
            Seg s = r.get(0);
            if (s.newline()) { r.remove(0); continue; }
            String t = s.text().stripLeading();
            if (t.isEmpty()) { r.remove(0); continue; }
            if (!t.equals(s.text())) r.set(0, s.withText(t));
            break;
        }
        while (!r.isEmpty()) {
            int i = r.size() - 1;
            Seg s = r.get(i);
            if (s.newline()) { r.remove(i); continue; }
            String t = s.text().stripTrailing();
            if (t.isEmpty()) { r.remove(i); continue; }
            if (!t.equals(s.text())) r.set(i, s.withText(t));
            break;
        }
        return r;
    }

    /** A paragraph from the runs, or null when there is nothing visible. */
    private static Paragraph paragraph(java.util.List<Seg> segs, Color color) {
        java.util.List<Seg> t = trimmed(segs);
        if (t.isEmpty()) return null;
        Paragraph p = new Paragraph();
        p.setMultipliedLeading(1.4f);
        fill(p, t, color);
        return p;
    }

    private static void fill(com.lowagie.text.Phrase target, java.util.List<Seg> segs, Color color) {
        for (Seg s : segs) {
            if (s.newline()) { target.add(Chunk.NEWLINE); continue; }
            Chunk c = new Chunk(s.text(), new Font(Font.HELVETICA, s.size(), s.style(), color));
            if (s.rise() != 0f) c.setTextRise(s.rise());
            target.add(c);
        }
    }

    // ── lists ────────────────────────────────────────────────────────────────

    private static com.lowagie.text.List list(org.jsoup.nodes.Element e, int depth) {
        boolean ordered = "ol".equals(e.tagName());
        com.lowagie.text.List list = new com.lowagie.text.List(ordered, 16f);
        list.setIndentationLeft(depth > 0 ? 12f : 6f);
        if (!ordered) list.setListSymbol(new Chunk("• ", new Font(Font.HELVETICA, BODY_SIZE, Font.NORMAL, DARK)));
        for (org.jsoup.nodes.Element li : e.children()) {
            if (!"li".equals(li.tagName())) continue;
            java.util.List<Seg> buf = new ArrayList<>();
            java.util.List<com.lowagie.text.List> nested = new ArrayList<>();
            java.util.List<Node> kids = li.childNodes();
            for (int i = 0; i < kids.size(); i++) {
                Node n = kids.get(i);
                if (n instanceof org.jsoup.nodes.Element c && ("ul".equals(c.tagName()) || "ol".equals(c.tagName()))) {
                    if (depth < MAX_DEPTH) nested.add(list(c, depth + 1));
                } else {
                    collect(n, Font.NORMAL, BODY_SIZE, 0f, buf);
                    // a paragraph inside an item ends its line
                    if (n instanceof org.jsoup.nodes.Element c && ("p".equals(c.tagName()) || "div".equals(c.tagName())) && i < kids.size() - 1)
                        buf.add(new Seg("", Font.NORMAL, BODY_SIZE, 0f, true));
                }
            }
            ListItem item = new ListItem();
            item.setMultipliedLeading(1.4f);
            fill(item, trimmed(buf), Color.BLACK);
            list.add(item);
            for (com.lowagie.text.List n : nested) list.add(n);
        }
        return list;
    }

    // ── tables ───────────────────────────────────────────────────────────────

    private static int span(org.jsoup.nodes.Element cell, String attr) {
        try {
            int v = Integer.parseInt(cell.attr(attr).trim());
            return Math.max(1, Math.min(v, MAX_COLS));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static java.util.List<org.jsoup.nodes.Element> cellsOf(org.jsoup.nodes.Element row) {
        java.util.List<org.jsoup.nodes.Element> cells = new ArrayList<>();
        for (org.jsoup.nodes.Element c : row.children()) if ("td".equals(c.tagName()) || "th".equals(c.tagName())) cells.add(c);
        return cells;
    }

    private static PdfPTable table(org.jsoup.nodes.Element tbl, int depth) {
        java.util.List<org.jsoup.nodes.Element> rows = new ArrayList<>();
        for (org.jsoup.nodes.Element c : tbl.children()) {
            if ("tr".equals(c.tagName())) rows.add(c);
            else if (c.tagName().matches("thead|tbody|tfoot"))
                for (org.jsoup.nodes.Element r : c.children()) if ("tr".equals(r.tagName())) rows.add(r);
        }
        int cols = 1;
        boolean rowspans = false;
        for (org.jsoup.nodes.Element row : rows) {
            int n = 0;
            for (org.jsoup.nodes.Element cell : cellsOf(row)) { n += span(cell, "colspan"); if (span(cell, "rowspan") > 1) rowspans = true; }
            cols = Math.max(cols, n);
        }
        cols = Math.min(cols, MAX_COLS);
        boolean any = rows.stream().anyMatch(r -> !cellsOf(r).isEmpty());
        if (!any) return null;

        PdfPTable t = new PdfPTable(cols);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(8);
        boolean headerRow = !cellsOf(rows.get(0)).isEmpty() && cellsOf(rows.get(0)).stream().allMatch(c -> "th".equals(c.tagName()));
        for (org.jsoup.nodes.Element row : rows) {
            int used = 0;
            for (org.jsoup.nodes.Element cell : cellsOf(row)) {
                if (used >= cols) break;
                boolean th = "th".equals(cell.tagName());
                PdfPCell pc = new PdfPCell();
                pc.setPadding(5);
                pc.setBorderColor(RULE);
                pc.setBorderWidth(0.5f);
                if (th) pc.setBackgroundColor(LIGHT);
                int cs = Math.max(1, Math.min(span(cell, "colspan"), cols - used));
                pc.setColspan(cs);
                int rs = Math.min(span(cell, "rowspan"), 20);
                if (rs > 1) pc.setRowspan(rs);
                java.util.List<Element> content = depth < MAX_DEPTH ? blocks(cell, depth + 1, th ? Font.BOLD : Font.NORMAL) : new ArrayList<>();
                for (Element el : content) pc.addElement(el);
                if (content.isEmpty()) pc.addElement(new Paragraph(" ", new Font(Font.HELVETICA, BODY_SIZE)));
                t.addCell(pc);
                used += cs;
            }
            if (!rowspans)
                while (used > 0 && used < cols) { t.addCell(emptyCell()); used++; }
        }
        if (headerRow) t.setHeaderRows(1);
        return t;
    }

    private static PdfPCell emptyCell() {
        PdfPCell c = new PdfPCell(new Paragraph(" ", new Font(Font.HELVETICA, BODY_SIZE)));
        c.setBorderColor(RULE);
        c.setBorderWidth(0.5f);
        return c;
    }

    // ── quote and rule ───────────────────────────────────────────────────────

    private static PdfPTable quote(org.jsoup.nodes.Element e, int depth) {
        PdfPTable q = new PdfPTable(2);
        q.setWidthPercentage(100);
        try { q.setWidths(new float[]{1f, 99f}); } catch (com.lowagie.text.DocumentException ignored) { /* two fixed widths cannot fail */ }
        q.setSpacingBefore(4);
        q.setSpacingAfter(8);
        PdfPCell bar = new PdfPCell(new Paragraph(" ", new Font(Font.HELVETICA, 4)));
        bar.setBackgroundColor(TEAL);
        bar.setBorder(Rectangle.NO_BORDER);
        q.addCell(bar);
        PdfPCell text = new PdfPCell();
        text.setBorder(Rectangle.NO_BORDER);
        text.setPaddingLeft(10);
        text.setPaddingTop(2);
        text.setPaddingBottom(2);
        java.util.List<Element> content = blocks(e, depth + 1, Font.ITALIC);
        for (Element el : content) text.addElement(el);
        if (content.isEmpty()) text.addElement(new Paragraph(" ", new Font(Font.HELVETICA, BODY_SIZE)));
        q.addCell(text);
        return q;
    }

    private static PdfPTable rule() {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(6);
        line.setSpacingAfter(6);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(0.8f);
        cell.setBackgroundColor(RULE);
        cell.setBorder(Rectangle.NO_BORDER);
        line.addCell(cell);
        return line;
    }
}
