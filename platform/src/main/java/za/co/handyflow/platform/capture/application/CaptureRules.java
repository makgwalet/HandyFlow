package za.co.handyflow.platform.capture.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure text rules: pick likely fields out of the text of a receipt or invoice.
 * Everything is a guess for a person to check, never a fact.
 */
public final class CaptureRules {
    private CaptureRules() {}

    public record Found(String supplierName, String documentNumber, LocalDate documentDate, LocalDate dueDate,
                        BigDecimal total, BigDecimal vatAmount, String supplierVatNumber, List<String> notes) {}

    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
    private static final String AMOUNT = "(?:R|ZAR)?\\s*(-?\\d{1,3}(?:[ \\u00a0,.]\\d{3})*(?:[.,]\\d{2})|-?\\d+(?:[.,]\\d{2})?)";
    private static final Pattern TOTAL = Pattern.compile(
            "(?im)^[^\\n]*\\b(grand\\s+total|total\\s+due|amount\\s+due|balance\\s+due|total\\s+payable|total\\s+incl\\w*|total)\\b[^\\n\\d]*" + AMOUNT);
    private static final Pattern VAT_LINE = Pattern.compile(
            "(?im)^[^\\n]*\\b(?:vat|tax)\\b(?!\\s*(?:no|number|reg|#))[^\\n\\d]*(?:15(?:[.,]0+)?\\s*%[^\\n\\d]*)?" + AMOUNT);
    private static final Pattern VAT_NO = Pattern.compile("(?i)vat\\s*(?:reg(?:istration)?\\.?\\s*)?(?:no\\.?|number|#)?\\s*[:.]?\\s*(4\\d{9})\\b");
    private static final Pattern DOC_NO = Pattern.compile(
            "(?i)\\b(?:tax\\s+)?(?:invoice|inv|receipt|slip|document|doc)\\s*(?:no\\.?|number|nr\\.?|#)\\s*[:.]?\\s*([A-Za-z0-9][A-Za-z0-9\\-/]{2,24})");
    private static final Pattern DATE_ISO = Pattern.compile("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b");
    private static final Pattern DATE_DMY = Pattern.compile("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})\\b");
    private static final Pattern DATE_WORDS = Pattern.compile("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([A-Za-z]{3,9})\\.?,?\\s+(\\d{4})\\b");
    private static final Pattern DUE = Pattern.compile("(?i)(?:due\\s*date|payment\\s*due|due\\s*on|due)\\s*[:.]?\\s*([^\\n]{6,24})");
    private static final Pattern DATE_LABEL = Pattern.compile("(?i)(?:invoice\\s*date|tax\\s*invoice\\s*date|date\\s*of\\s*issue|issued|date)\\s*[:.]?\\s*([^\\n]{6,24})");

    /** Amount text to number: handles "1 234,56", "1,234.56", "1234.56". Null when it is not an amount. */
    public static BigDecimal amount(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("(?i)^(R|ZAR)", "").trim().replace(" ", " ");
        if (s.isEmpty()) return null;
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        int dec = Math.max(lastDot, lastComma);
        String digits;
        if (dec >= 0 && s.length() - dec - 1 == 2) {
            digits = s.substring(0, dec).replaceAll("[^0-9-]", "") + "." + s.substring(dec + 1);
        } else {
            digits = s.replaceAll("[^0-9-]", "");
        }
        try {
            return new BigDecimal(digits).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** First date found in the fragment, or null. Day first for slashes, as in South Africa. */
    public static LocalDate date(String frag) {
        if (frag == null) return null;
        Matcher m = DATE_ISO.matcher(frag);
        if (m.find()) {
            LocalDate d = make(num(m.group(1)), num(m.group(2)), num(m.group(3)));
            if (d != null) return d;
        }
        m = DATE_DMY.matcher(frag);
        if (m.find()) {
            LocalDate d = make(num(m.group(3)), num(m.group(2)), num(m.group(1)));
            if (d != null) return d;
        }
        m = DATE_WORDS.matcher(frag);
        while (m.find()) {
            int mon = month(m.group(2));
            if (mon > 0) {
                LocalDate d = make(num(m.group(3)), mon, num(m.group(1)));
                if (d != null) return d;
            }
        }
        return null;
    }

    public static Found parse(String text, LocalDate today) {
        List<String> notes = new ArrayList<>();
        if (text == null || text.isBlank()) {
            notes.add("No text could be read from this file.");
            return new Found(null, null, null, null, null, null, null, notes);
        }
        String t = text.replace("\r", "");

        String docNo = null;
        Matcher m = DOC_NO.matcher(t);
        if (m.find()) docNo = m.group(1);

        String vatNo = null;
        m = VAT_NO.matcher(t);
        if (m.find()) vatNo = m.group(1);

        LocalDate date = null;
        m = DATE_LABEL.matcher(t);
        while (date == null && m.find()) {
            String lead = t.substring(Math.max(0, m.start() - 4), m.start()).toLowerCase(Locale.ROOT);
            if (lead.endsWith("due ") || lead.endsWith("due")) continue;
            date = date(m.group(1));
        }
        LocalDate due = null;
        m = DUE.matcher(t);
        if (m.find()) due = date(m.group(1));
        if (date == null) date = date(t);
        if (date != null && today != null && date.isAfter(today.plusDays(1))) {
            notes.add("The date read (" + date + ") is in the future. Check it.");
        }
        if (due != null && date != null && due.isBefore(date)) {
            notes.add("The due date is before the invoice date. Check both.");
            due = null;
        }

        BigDecimal total = null;
        m = TOTAL.matcher(t);
        BigDecimal best = null;
        int bestRank = -1;
        while (m.find()) {
            BigDecimal v = amount(m.group(2));
            if (v == null || v.signum() <= 0) continue;
            String label = m.group(1).toLowerCase(Locale.ROOT);
            String line = t.substring(t.lastIndexOf('\n', m.start(1)) + 1, m.start(1)).toLowerCase(Locale.ROOT);
            if (line.contains("sub")) continue;
            int rank = label.startsWith("grand") || label.startsWith("total due") || label.startsWith("amount due")
                    || label.startsWith("balance due") || label.startsWith("total payable") || label.startsWith("total incl") ? 2 : 1;
            if (rank > bestRank || (rank == bestRank && v.compareTo(best) > 0)) {
                best = v;
                bestRank = rank;
            }
        }
        total = best;

        BigDecimal vat = null;
        m = VAT_LINE.matcher(t);
        while (m.find()) {
            BigDecimal v = amount(m.group(1));
            if (v != null && v.signum() > 0 && (total == null || v.compareTo(total) < 0)) {
                vat = v;
                break;
            }
        }
        if (total != null && vat != null) {
            BigDecimal expected = total.multiply(new BigDecimal("15")).divide(new BigDecimal("115"), 2, RoundingMode.HALF_UP);
            if (expected.subtract(vat).abs().compareTo(new BigDecimal("0.05")) > 0) {
                notes.add("The VAT read (" + vat + ") does not equal 15% of the total (" + expected + "). Check it.");
            }
        }

        String supplier = supplier(t);

        if (total == null) notes.add("No total found.");
        if (docNo == null) notes.add("No invoice or receipt number found.");
        if (date == null) notes.add("No date found.");
        if (supplier == null) notes.add("Could not tell who the supplier is.");
        notes.add("These are suggestions read from the file. Check every field before saving.");
        return new Found(supplier, docNo, date, due, total, vat, vatNo, notes);
    }

    /** The first line near the top that looks like a business name rather than a heading or a number. */
    static String supplier(String t) {
        int seen = 0;
        for (String raw : t.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (++seen > 8) break;
            String low = line.toLowerCase(Locale.ROOT);
            if (low.matches("^(tax\\s+)?(invoice|receipt|statement|quotation|credit note|page.*|original|copy|duplicate)\\W*$")) continue;
            if (line.length() < 3 || line.length() > 80) continue;
            long letters = line.chars().filter(Character::isLetter).count();
            if (letters < 3 || letters * 2 < line.length()) continue;
            if (low.contains("@") || low.startsWith("www") || low.startsWith("http") || low.matches(".*\\b(vat|tel|phone|fax|date|invoice no|reg)\\b.*")) continue;
            return line;
        }
        return null;
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    private static int month(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (w.length() < 3) return 0;
        for (int i = 0; i < MONTHS.length; i++) if (w.startsWith(MONTHS[i])) return i + 1;
        return 0;
    }

    private static LocalDate make(int y, int mo, int d) {
        if (y < 2000 || y > 2100 || mo < 1 || mo > 12 || d < 1) return null;
        if (d > YearMonth.of(y, mo).lengthOfMonth()) return null;
        return LocalDate.of(y, mo, d);
    }
}
