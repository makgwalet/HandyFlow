package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Letterhead;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Which letterhead a document uses, and whether one is fit to save (patch 0193, CLINIC-DEC-026). Pure. */
public final class LetterheadRules {

    public static final int NAME_MAX = 120;
    public static final int LINES_MAX = 6;
    public static final int LINE_MAX = 120;
    public static final int LOGO_MAX_BYTES = 300 * 1024;

    private LetterheadRules() {}

    public record Row(UUID id, String name, String branchName, String practiceName, List<String> lines, boolean isDefault, boolean active) {}

    /** Reasons the letterhead cannot be saved. */
    public static List<String> problems(String name, String practiceName, List<String> lines, byte[] logo) {
        List<String> out = new ArrayList<>();
        if (blank(name)) out.add("Give the letterhead a name");
        else if (name.trim().length() > NAME_MAX) out.add("The name is longer than " + NAME_MAX + " characters");
        if (blank(practiceName)) out.add("Give the practice name that is printed at the top");
        else if (practiceName.trim().length() > NAME_MAX) out.add("The practice name is longer than " + NAME_MAX + " characters");
        if (lines != null) {
            if (lines.size() > LINES_MAX) out.add("Use at most " + LINES_MAX + " address lines");
            if (lines.stream().anyMatch(l -> l != null && l.trim().length() > LINE_MAX)) out.add("An address line is longer than " + LINE_MAX + " characters");
        }
        if (logo != null && logo.length > 0) {
            if (logo.length > LOGO_MAX_BYTES) out.add("The logo is larger than " + (LOGO_MAX_BYTES / 1024) + " KB");
            if (imageType(logo) == null) out.add("The logo must be a PNG or JPEG picture");
        }
        return out;
    }

    /** The type from the file's own first bytes, never from its name. */
    public static String imageType(byte[] b) {
        if (b == null || b.length < 8) return null;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    /** Lines trimmed, blanks removed. */
    public static List<String> tidy(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines != null) for (String l : lines) if (!blank(l)) out.add(l.trim());
        return out;
    }

    /** The chosen letterhead if it is active; else the one for the branch name; else the practice-wide default; else none. */
    public static Row pick(List<Row> all, UUID chosen, String branchName) {
        List<Row> live = all == null ? List.of() : all.stream().filter(Row::active).toList();
        if (chosen != null) {
            for (Row r : live) if (r.id().equals(chosen)) return r;
        }
        if (!blank(branchName)) {
            String b = branchName.trim().toLowerCase(Locale.ROOT);
            for (Row r : live) if (r.branchName() != null && r.branchName().trim().toLowerCase(Locale.ROOT).equals(b)) return r;
        }
        for (Row r : live) if (r.isDefault()) return r;
        return null;
    }

    /** What is printed: for a branch the branch name follows the practice name. */
    public static Letterhead toLetterhead(Row row, byte[] logo) {
        if (row == null) return null;
        String top = blank(row.branchName()) || row.practiceName().toLowerCase(Locale.ROOT).contains(row.branchName().trim().toLowerCase(Locale.ROOT))
                ? row.practiceName().trim() : row.practiceName().trim() + " – " + row.branchName().trim();
        return new Letterhead(top, row.lines(), logo);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
