package za.co.handyflow.platform.legalcompliance.application.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a report table as an Excel workbook (.xlsx) or a PDF, with no third-party library: both formats are small enough to write directly, which keeps them testable
 * without the application context. Pure functions of their inputs.
 *
 * Safety: in the workbook every text cell is an inline string, so a cell that begins with "=" is shown as text and is never evaluated as a formula. PDF text is
 * encoded to Windows-1252 (everything South African names need); anything outside it prints as "?" rather than breaking the file.
 */
public final class ReportFiles {

    private ReportFiles() {}

    public static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String PDF_TYPE = "application/pdf";

    // ═══════════════════════════════════════════════════════════════════ XLSX

    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9]\\d{0,14})(\\.\\d{1,6})?");
    private static final int HEADER_ROW = 4, MAX_CELL = 32000;

    public static byte[] xlsx(String title, String subtitle, List<String> columns, List<List<String>> rows) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(z, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"" + xml(sheetName(title)) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(z, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            put(z, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1B3A6B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");

            int ncol = Math.max(1, columns.size());
            StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            s.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"").append(HEADER_ROW).append("\" topLeftCell=\"A").append(HEADER_ROW + 1).append("\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
            s.append("<cols>");
            for (int c = 0; c < ncol; c++) s.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"").append(columnWidth(columns, rows, c)).append("\" customWidth=\"1\"/>");
            s.append("</cols><sheetData>");
            s.append(row(1, List.of(title), 1)).append(row(2, List.of(subtitle == null ? "" : subtitle), 0));
            s.append(row(HEADER_ROW, columns, 2));
            int r = HEADER_ROW + 1;
            for (List<String> data : rows) s.append(row(r++, data, 0));
            s.append("</sheetData><autoFilter ref=\"A").append(HEADER_ROW).append(":").append(colName(ncol - 1)).append(HEADER_ROW).append("\"/></worksheet>");
            put(z, "xl/worksheets/sheet1.xml", s.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String row(int number, List<String> cells, int style) {
        StringBuilder sb = new StringBuilder("<row r=\"").append(number).append("\">");
        for (int c = 0; c < cells.size(); c++) {
            String v = clean(cells.get(c));
            String ref = colName(c) + number;
            if (style == 0 && NUMBER.matcher(v).matches()) sb.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
            else if (v.isEmpty()) continue;
            else sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(style == 0 ? "" : " s=\"" + style + "\"").append("><is><t xml:space=\"preserve\">").append(xml(v)).append("</t></is></c>");
        }
        return sb.append("</row>").toString();
    }

    static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = index; i >= 0; i = i / 26 - 1) sb.insert(0, (char) ('A' + i % 26));
        return sb.toString();
    }

    private static int columnWidth(List<String> columns, List<List<String>> rows, int c) {
        int w = c < columns.size() ? clean(columns.get(c)).length() : 8;
        for (int i = 0; i < Math.min(rows.size(), 300); i++) { List<String> r = rows.get(i); if (c < r.size()) w = Math.max(w, clean(r.get(c)).length()); }
        return Math.max(8, Math.min(60, w + 2));
    }

    /** Excel sheet names: at most 31 characters and none of  [ ] : * ? / \ . */
    static String sheetName(String title) {
        String s = clean(title).replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (s.isEmpty()) s = "Report";
        return s.length() > 31 ? s.substring(0, 31).trim() : s;
    }

    /** Removes characters XML cannot carry and caps the length of a cell. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(Math.min(s.length(), MAX_CELL));
        s.codePoints().forEach(cp -> {
            boolean ok = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (ok && sb.length() < MAX_CELL) sb.appendCodePoint(cp);
        });
        return sb.toString();
    }

    static String xml(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private static void put(ZipOutputStream z, String name, String body) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(body.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    // ═══════════════════════════════════════════════════════════════════ PDF

    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final double PW = 842, PH = 595, MARGIN = 36, PAD = 3;
    private static final int MAX_LINES = 8;
    private static final int[] W_REG = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500,334,260,334,584,278,556,278,222,556,333,1000,556,556,333,1000,667,333,1000,278,611,278,278,222,222,333,333,350,556,1000,333,1000,500,333,944,278,500,667,278,333,556,556,556,556,260,556,333,737,370,556,584,333,737,333,400,584,333,333,333,556,537,278,333,333,365,556,834,834,834,611,667,667,667,667,667,667,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,500,556,556,556,556,278,278,278,278,556,556,556,556,556,556,556,584,611,556,556,556,556,500,556,500};
    private static final int[] W_BOLD = {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,611,611,389,556,333,611,556,778,556,556,500,389,280,389,584,278,556,278,278,556,500,1000,556,556,333,1000,667,333,1000,278,611,278,278,278,278,500,500,350,556,1000,333,1000,556,333,944,278,500,667,278,333,556,556,556,556,280,556,333,737,370,556,584,333,737,333,400,584,333,333,333,611,556,278,333,333,365,556,834,834,834,611,722,722,722,722,722,722,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,556,556,556,556,556,278,278,278,278,611,611,611,611,611,611,611,584,611,611,611,611,611,556,611,556};

    /** One report in a PDF: its own title, a line under it, the columns and the rows. */
    public record Section(String title, String subtitle, List<String> columns, List<List<String>> rows) {}

    public static byte[] pdf(String title, String subtitle, List<String> columns, List<List<String>> rows) {
        return build(null, null, List.of(new Section(title, subtitle, columns, rows)));
    }

    /**
     * A pack of several reports in one PDF: a cover page with a contents list and page numbers, then each report starting on a new page. Page numbers run across the
     * whole pack ("Page 7 of 31"). An empty section list gives a cover page that says so.
     */
    public static byte[] pdfPack(String title, String subtitle, List<Section> sections) {
        return build(title == null || title.isBlank() ? "Report pack" : title, subtitle, sections);
    }

    /** A section after layout: its rows already wrapped and split into pages. */
    private static final class Laid {
        Section s; double size, lead, headerH, firstTop, otherTop; double[] widths; String[] header; List<String> subLines;
        List<List<String[]>> pages = new ArrayList<>(); List<List<Double>> heights = new ArrayList<>();
    }

    private static Laid layout(Section s) {
        Laid L = new Laid();
        L.s = s;
        List<String> columns = s.columns();
        int ncol = Math.max(1, columns.size());
        L.size = ncol > 16 ? 5.5 : ncol > 12 ? 6.5 : 7.5;
        L.lead = L.size * 1.25;
        double avail = PW - 2 * MARGIN;
        L.widths = columnWidths(columns, s.rows(), L.size, avail);
        L.header = wrapRow(columns, L.widths, L.size, true);
        L.headerH = height(L.header, L.lead);
        L.subLines = wrap(s.subtitle() == null ? "" : s.subtitle(), avail, 8, false, 3);
        L.firstTop = PH - MARGIN - 16 - L.subLines.size() * 10 - 6;
        L.otherTop = PH - MARGIN - 16 - 6;
        double bottom = MARGIN + 14, y = L.firstTop - L.headerH;
        L.pages.add(new ArrayList<>()); L.heights.add(new ArrayList<>());
        for (List<String> data : s.rows()) {
            String[] cells = wrapRow(data, L.widths, L.size, false);
            double h = height(cells, L.lead);
            if (y - h < bottom) { L.pages.add(new ArrayList<>()); L.heights.add(new ArrayList<>()); y = L.otherTop - L.headerH; }
            L.pages.get(L.pages.size() - 1).add(cells); L.heights.get(L.heights.size() - 1).add(h);
            y -= h;
        }
        return L;
    }

    private static void drawPage(Page pg, Laid L, int p) {
        pg.text(MARGIN, PH - MARGIN - 12, 13, true, p == 0 ? L.s.title() : L.s.title() + " (continued)", 0.1, 0.14, 0.25);
        double ty = L.otherTop;
        if (p == 0) {
            for (int i = 0; i < L.subLines.size(); i++) pg.text(MARGIN, PH - MARGIN - 16 - 10 * (i + 1) + 2, 8, false, L.subLines.get(i), 0.39, 0.45, 0.55);
            ty = L.firstTop;
        }
        drawRow(pg, L.header, L.widths, ty, L.headerH, L.size, L.lead, true, 0);
        ty -= L.headerH;
        for (int i = 0; i < L.pages.get(p).size(); i++) {
            double h = L.heights.get(p).get(i);
            drawRow(pg, L.pages.get(p).get(i), L.widths, ty, h, L.size, L.lead, false, i);
            ty -= h;
        }
    }

    private static final int MAX_CONTENTS = 34;

    private static byte[] build(String coverTitle, String coverSub, List<Section> sections) {
        List<Laid> laid = new ArrayList<>();
        for (Section s : sections) laid.add(layout(s));
        boolean cover = coverTitle != null;
        int total = (cover ? 1 : 0);
        for (Laid L : laid) total += L.pages.size();

        List<byte[]> streams = new ArrayList<>();
        int pageNo = 0;
        if (cover) {
            pageNo++;
            Page pg = new Page();
            pg.fill(0, PH - 150, PW, 150, 0.106, 0.227, 0.42);
            pg.text(MARGIN, PH - 80, 26, true, clip(coverTitle, 70), 1, 1, 1);
            double y = PH - 110;
            for (String l : wrap(coverSub == null ? "" : coverSub, PW - 2 * MARGIN, 10, false, 2)) { pg.text(MARGIN, y, 10, false, l, 0.85, 0.89, 0.95); y -= 13; }
            y = PH - 190;
            pg.text(MARGIN, y, 12, true, "Contents", 0.1, 0.14, 0.25);
            y -= 20;
            if (laid.isEmpty()) pg.text(MARGIN, y, 10, false, "No reports were selected.", 0.39, 0.45, 0.55);
            int first = 2;
            for (int i = 0; i < laid.size() && i < MAX_CONTENTS; i++) {
                String t = (i + 1) + ".  " + clip(laid.get(i).s.title(), 90), n = String.valueOf(first);
                pg.text(MARGIN, y, 10, false, t, 0.06, 0.09, 0.16);
                pg.text(PW - MARGIN - width(n, 10, false), y, 10, false, n, 0.06, 0.09, 0.16);
                y -= 13;
                first += laid.get(i).pages.size();
            }
            if (laid.size() > MAX_CONTENTS) pg.text(MARGIN, y, 9, false, "… and " + (laid.size() - MAX_CONTENTS) + " more, in the pages that follow.", 0.39, 0.45, 0.55);
            footer(pg, pageNo, total);
            streams.add(pg.bytes());
        }
        for (Laid L : laid) {
            for (int p = 0; p < L.pages.size(); p++) {
                pageNo++;
                Page pg = new Page();
                drawPage(pg, L, p);
                footer(pg, pageNo, total);
                streams.add(pg.bytes());
            }
        }
        return assemble(streams);
    }

    private static void footer(Page pg, int pageNo, int total) {
        pg.text(MARGIN, MARGIN - 4, 7, false, "HandyFlow Legal & Compliance", 0.39, 0.45, 0.55);
        String pn = "Page " + pageNo + " of " + total;
        pg.text(PW - MARGIN - width(pn, 7, false), MARGIN - 4, 7, false, pn, 0.39, 0.45, 0.55);
    }

    private static void drawRow(Page pg, String[] cells, double[] widths, double top, double h, double size, double lead, boolean header, int index) {
        double x = MARGIN;
        if (header) pg.fill(x, top - h, sum(widths), h, 0.106, 0.227, 0.42);
        else if (index % 2 == 1) pg.fill(x, top - h, sum(widths), h, 0.973, 0.98, 0.988);
        for (int c = 0; c < widths.length; c++) {
            if (!header) pg.stroke(x, top - h, widths[c], h);
            String text = c < cells.length ? cells[c] : "";
            String[] lines = text.isEmpty() ? new String[0] : text.split("\\n", -1);
            for (int l = 0; l < lines.length; l++)
                pg.text(x + PAD, top - PAD - size - l * lead + 1, size, header, lines[l], header ? 1 : 0.06, header ? 1 : 0.09, header ? 1 : 0.16);
            x += widths[c];
        }
    }

    private static double height(String[] cells, double lead) {
        int lines = 1;
        for (String c : cells) if (!c.isEmpty()) lines = Math.max(lines, c.split("\\n", -1).length);
        return lines * lead + 2 * PAD;
    }

    private static String[] wrapRow(List<String> data, double[] widths, double size, boolean bold) {
        String[] out = new String[widths.length];
        for (int c = 0; c < widths.length; c++)
            out[c] = String.join("\n", wrap(c < data.size() ? data.get(c) : "", widths[c] - 2 * PAD, size, bold, MAX_LINES));
        return out;
    }

    private static double[] columnWidths(List<String> columns, List<List<String>> rows, double size, double avail) {
        int n = Math.max(1, columns.size());
        double[] w = new double[n];
        for (int c = 0; c < n; c++) {
            double m = width(shortest(c < columns.size() ? columns.get(c) : "", 14), size, true) + 2 * PAD;
            double content = 0;
            for (int i = 0; i < Math.min(rows.size(), 200); i++) { List<String> r = rows.get(i); if (c < r.size()) content = Math.max(content, width(clip(r.get(c), 60), size, false)); }
            w[c] = Math.max(m, Math.min(content + 2 * PAD, 190));
        }
        double total = sum(w);
        for (int c = 0; c < n; c++) w[c] = w[c] * avail / total;
        return w;
    }

    /** Greedy word wrap to a width in points, at most maxLines lines (the last ends in "..." when text was cut). A word wider than the line is split. */
    static List<String> wrap(String text, double widthPts, double size, boolean bold, int maxLines) {
        List<String> lines = new ArrayList<>();
        String t = text == null ? "" : text.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (t.isEmpty()) return lines;
        StringBuilder cur = new StringBuilder();
        boolean cut = false;
        for (String word : t.split(" +")) {
            while (width(word, size, bold) > widthPts) {           // split an over-long word
                int k = 1;
                while (k < word.length() && width(word.substring(0, k + 1), size, bold) <= widthPts) k++;
                if (cur.length() > 0) { lines.add(cur.toString()); cur.setLength(0); }
                lines.add(word.substring(0, k));
                word = word.substring(k);
            }
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (width(trial, size, bold) <= widthPts) { cur.setLength(0); cur.append(trial); }
            else { lines.add(cur.toString()); cur.setLength(0); cur.append(word); }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        if (lines.size() > maxLines) { lines = new ArrayList<>(lines.subList(0, maxLines)); cut = true; }
        if (cut) {
            String last = lines.get(maxLines - 1);
            while (last.length() > 1 && width(last + "...", size, bold) > widthPts) last = last.substring(0, last.length() - 1);
            lines.set(maxLines - 1, last + "...");
        }
        return lines;
    }

    /** Width of text in points at the given size, using Helvetica metrics. Characters outside Windows-1252 count as "?". */
    static double width(String s, double size, boolean bold) {
        int[] w = bold ? W_BOLD : W_REG;
        double total = 0;
        for (byte b : s.getBytes(CP1252)) total += w[b & 0xFF];
        return total * size / 1000.0;
    }

    private static String clip(String s, int n) { return s == null ? "" : s.length() > n ? s.substring(0, n) : s; }

    private static String shortest(String s, int n) { String t = s == null ? "" : s; int sp = t.indexOf(' '); return t.length() > n ? t.substring(0, n) : (sp > 0 ? t.substring(0, Math.min(sp, n)) : t); }

    private static double sum(double[] a) { double t = 0; for (double v : a) t += v; return t; }

    /** One page's drawing commands. */
    private static final class Page {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void raw(String s) { out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1)); }

        void fill(double x, double y, double w, double h, double r, double g, double b) { raw(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f\n", r, g, b, x, y, w, h)); }

        void stroke(double x, double y, double w, double h) { raw(String.format(java.util.Locale.ROOT, "0.886 0.91 0.941 RG 0.4 w %.2f %.2f %.2f %.2f re S\n", x, y, w, h)); }

        void text(double x, double y, double size, boolean bold, String s, double r, double g, double b) {
            raw(String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f rg BT /%s %.2f Tf %.2f %.2f Td (", r, g, b, bold ? "F2" : "F1", size, x, y));
            for (byte c : s.getBytes(CP1252)) {
                int v = c & 0xFF;
                if (v == '(' || v == ')' || v == '\\') { out.write('\\'); out.write(v); }
                else if (v < 32 || v > 126) raw(String.format("\\%03o", v));
                else out.write(v);
            }
            raw(") Tj ET\n");
        }

        byte[] bytes() { return out.toByteArray(); }
    }

    private static byte[] assemble(List<byte[]> streams) {
        try {
            ByteArrayOutputStream f = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();
            f.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
            int n = streams.size();
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < n; i++) kids.append(5 + 2 * i).append(" 0 R ");
            obj(f, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
            obj(f, offsets, "<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + n + " >>");
            obj(f, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
            obj(f, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
            for (int i = 0; i < n; i++) {
                obj(f, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) PW + " " + (int) PH + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (6 + 2 * i) + " 0 R >>");
                ByteArrayOutputStream z = new ByteArrayOutputStream();
                try (DeflaterOutputStream d = new DeflaterOutputStream(z, new Deflater(Deflater.BEST_COMPRESSION))) { d.write(streams.get(i)); }
                byte[] data = z.toByteArray();
                offsets.add(f.size());
                f.write(((6 + 2 * i) + " 0 obj\n<< /Filter /FlateDecode /Length " + data.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
                f.write(data);
                f.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
            }
            int xref = f.size();
            StringBuilder x = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
            for (int o : offsets) x.append(String.format("%010d 00000 n \n", o));
            x.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
            f.write(x.toString().getBytes(StandardCharsets.ISO_8859_1));
            return f.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void obj(ByteArrayOutputStream f, List<Integer> offsets, String body) throws IOException {
        offsets.add(f.size());
        f.write(((offsets.size()) + " 0 obj\n" + body + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
    }
}
