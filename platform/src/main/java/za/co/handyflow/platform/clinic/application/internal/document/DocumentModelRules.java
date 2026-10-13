package za.co.handyflow.platform.clinic.application.internal.document;

import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.*;

import java.util.ArrayList;
import java.util.List;

/** Checks a model before any renderer sees it, and gives the plain text a renderer's output must contain. Pure. */
public final class DocumentModelRules {

    public static final int MAX_BLOCKS = 400;
    public static final int MAX_TABLE_ROWS = 500;
    public static final int MAX_TEXT = 20000;
    public static final String DASH = "–";

    private DocumentModelRules() {}

    /** Reasons the model cannot be rendered; empty when it can. */
    public static List<String> problems(DocumentModel m) {
        List<String> out = new ArrayList<>();
        if (m == null) return List.of("There is no document");
        if (isBlank(m.title())) out.add("The document has no title");
        if (m.blocks().isEmpty()) out.add("The document has no content");
        if (m.blocks().size() > MAX_BLOCKS) out.add("The document has more than " + MAX_BLOCKS + " blocks");
        int i = 0;
        for (Block b : m.blocks()) {
            i++;
            String at = "Block " + i + ": ";
            if (b instanceof Heading h) {
                if (h.level() < 1 || h.level() > 3) out.add(at + "heading level must be 1 to 3");
                if (isBlank(h.text())) out.add(at + "heading is empty");
            } else if (b instanceof TextBlock p) {
                if (p.text() != null && p.text().length() > MAX_TEXT) out.add(at + "paragraph is longer than " + MAX_TEXT + " characters");
            } else if (b instanceof KeyValues kv) {
                for (String[] pair : kv.pairs()) if (pair == null || pair.length != 2 || isBlank(pair[0])) out.add(at + "each detail needs a label and a value");
            } else if (b instanceof Grid t) {
                if (t.header().isEmpty()) out.add(at + "table has no header row");
                if (t.weights().size() != t.header().size()) out.add(at + "table needs one width for each column");
                if (t.weights().stream().anyMatch(w -> w == null || w <= 0)) out.add(at + "column widths must be above zero");
                if (t.rows().size() > MAX_TABLE_ROWS) out.add(at + "table has more than " + MAX_TABLE_ROWS + " rows");
                for (List<String> r : t.rows()) if (r.size() != t.header().size()) { out.add(at + "a row does not have " + t.header().size() + " cells"); break; }
            } else if (b instanceof Spacer s) {
                if (s.points() < 0 || s.points() > 200) out.add(at + "space must be 0 to 200 points");
            } else if (b instanceof Signature s) {
                if (isBlank(s.signerName())) out.add(at + "signature block has no signer");
            }
        }
        return out;
    }

    /** What a person would read: text only, in order, with the same dash for blanks that a renderer prints. Used to compare renderers. */
    public static String text(DocumentModel m) {
        StringBuilder sb = new StringBuilder();
        if (m.letterhead() != null) {
            sb.append(clean(m.letterhead().name())).append('\n');
            for (String l : m.letterhead().lines()) sb.append(clean(l)).append('\n');
        }
        for (Block b : m.blocks()) {
            if (b instanceof Heading h) sb.append(clean(h.text())).append('\n');
            else if (b instanceof TextBlock p) sb.append(clean(p.text())).append('\n');
            else if (b instanceof KeyValues kv) for (String[] pair : kv.pairs()) sb.append(clean(pair[0])).append(": ").append(orDash(pair[1])).append('\n');
            else if (b instanceof Grid t) {
                sb.append(String.join(" | ", t.header().stream().map(DocumentModelRules::clean).toList())).append('\n');
                for (List<String> r : t.rows()) sb.append(String.join(" | ", r.stream().map(DocumentModelRules::orDash).toList())).append('\n');
            } else if (b instanceof Signature s) {
                if (!isBlank(s.closing())) sb.append(clean(s.closing())).append('\n');
                sb.append(clean(s.signerName())).append('\n');
                if (!isBlank(s.signerLine())) sb.append(clean(s.signerLine())).append('\n');
            }
        }
        if (!isBlank(m.footerText())) sb.append(clean(m.footerText())).append('\n');
        return sb.toString();
    }

    /** Blank becomes a dash so a missing optional field is visible and never leaves a gap. */
    public static String orDash(String s) {
        return isBlank(s) ? DASH : clean(s);
    }

    /** Removes control characters (keeps new lines and tabs as spaces) and trims. Renderers call this on every string. */
    public static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t') sb.append(' ');
            else if (c == '\n' || c == '\r' || !Character.isISOControl(c)) sb.append(c);
        }
        return sb.toString().trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
