package za.co.handyflow.platform.recruiter.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a CSV of candidates to add to a job. Pure. The first line must be a header; columns are found by name (any order, any
 * capitalisation), so a spreadsheet export works as it is. A comma or a semicolon may separate the columns, and a value may be quoted.
 */
public final class RecruiterImportRules {
    private RecruiterImportRules() {}

    public static final int MAX_ROWS = 500;
    public static final int MAX_CHARS = 1_000_000;
    public static final int MAX_PROBLEMS_SHOWN = 50;
    public static final String SOURCE = "IMPORT";

    /** One candidate line. {@code line} is the line number in the file (the header is line 1). */
    public record Row(int line, String firstName, String lastName, String email, String phone, String location,
                      String linkedinUrl, String portfolioUrl, String source) {}

    /** What was read: the candidates, the lines that could not be used with the reason, and a problem with the file as a whole. */
    public record Parsed(List<Row> rows, Map<Integer, String> badLines, String fileProblem) {}

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    static {
        for (String s : new String[]{"firstname", "first name", "first_name", "first", "name", "given name"}) ALIASES.put(s, "firstName");
        for (String s : new String[]{"lastname", "last name", "last_name", "last", "surname", "family name"}) ALIASES.put(s, "lastName");
        for (String s : new String[]{"email", "email address", "e-mail", "mail"}) ALIASES.put(s, "email");
        for (String s : new String[]{"phone", "phone number", "mobile", "cell", "cellphone", "telephone"}) ALIASES.put(s, "phone");
        for (String s : new String[]{"location", "city", "town", "province"}) ALIASES.put(s, "location");
        for (String s : new String[]{"linkedin", "linkedin url", "linkedinurl", "linkedin profile"}) ALIASES.put(s, "linkedinUrl");
        for (String s : new String[]{"portfolio", "portfolio url", "portfolio link", "website"}) ALIASES.put(s, "portfolioUrl");
        for (String s : new String[]{"source", "origin"}) ALIASES.put(s, "source");
    }

    /** Why this lawful-basis answer cannot start an import, or null. The people were not asked through the careers form. */
    public static String basisProblem(Boolean confirmed) {
        return Boolean.TRUE.equals(confirmed) ? null
                : "Confirm that you have a lawful reason to hold these people's details (for example they sent you their CV or agreed to be contacted).";
    }

    public static Parsed parse(String csv) {
        if (csv == null || csv.isBlank()) return fail("The file is empty.");
        if (csv.length() > MAX_CHARS) return fail("The file is too large (about 1 MB at most).");
        if (csv.charAt(0) == '﻿') csv = csv.substring(1);
        char sep = separator(csv);
        List<List<String>> lines = split(csv, sep);
        if (lines.isEmpty()) return fail("The file is empty.");

        Map<String, Integer> col = new LinkedHashMap<>();
        List<String> header = lines.get(0);
        for (int i = 0; i < header.size(); i++) {
            String key = ALIASES.get(header.get(i).trim().toLowerCase(Locale.ROOT));
            if (key != null && !col.containsKey(key)) col.put(key, i);
        }
        if (!col.containsKey("email")) return fail("The first line must name the columns, and one of them must be \"email\".");
        boolean lastNameColumn = col.containsKey("lastName");
        if (!col.containsKey("firstName")) return fail("The first line must include a \"first name\" column.");
        if (lines.size() - 1 > MAX_ROWS) return fail("Too many rows (" + MAX_ROWS + " at most in one import). Split the file.");

        List<Row> rows = new ArrayList<>();
        Map<Integer, String> bad = new LinkedHashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int n = 1; n < lines.size(); n++) {
            List<String> f = lines.get(n);
            int lineNo = n + 1;
            String first = cell(f, col.get("firstName")), last = cell(f, col.get("lastName"));
            if (!lastNameColumn || last.isEmpty()) {
                // A single "name" column holds both: split on the last space.
                int sp = first.lastIndexOf(' ');
                if (sp > 0 && !lastNameColumn) { last = first.substring(sp + 1).trim(); first = first.substring(0, sp).trim(); }
            }
            String email = RecruiterPublicRules.normaliseEmail(cell(f, col.get("email")));
            String source = cell(f, col.get("source"));
            String problem = RecruiterPublicRules.applicationProblem(first, last, email, cell(f, col.get("phone")), cell(f, col.get("location")),
                    cell(f, col.get("linkedinUrl")), cell(f, col.get("portfolioUrl")), source.isEmpty() ? null : source, null);
            if (problem == null && !seen.add(email)) problem = "This email appears earlier in the file.";
            if (problem != null) { bad.put(lineNo, problem); continue; }
            rows.add(new Row(lineNo, first, last, email, cell(f, col.get("phone")), cell(f, col.get("location")),
                    cell(f, col.get("linkedinUrl")), cell(f, col.get("portfolioUrl")), SOURCE));
        }
        return new Parsed(rows, bad, null);
    }

    private static Parsed fail(String why) { return new Parsed(List.of(), Map.of(), why); }

    private static String cell(List<String> f, Integer i) {
        if (i == null || i >= f.size()) return "";
        String v = f.get(i).trim();
        // A spreadsheet can turn a cell into a formula; never keep one.
        if (!v.isEmpty() && "=+@".indexOf(v.charAt(0)) >= 0 && !v.matches("^\\+[0-9 ()\\-.]{5,30}$")) v = v.substring(1).trim();
        return v;
    }

    private static char separator(String csv) {
        int nl = csv.indexOf('\n');
        String head = nl < 0 ? csv : csv.substring(0, nl);
        long commas = head.chars().filter(c -> c == ',').count(), semis = head.chars().filter(c -> c == ';').count();
        return semis > commas ? ';' : ',';
    }

    /** Splits into lines of values, honouring quotes (a quoted value may hold the separator, a line break and a doubled quote). Blank lines are dropped. */
    static List<List<String>> split(String csv, char sep) {
        List<List<String>> out = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') { sb.append('"'); i++; } else quoted = false;
                } else sb.append(c);
            } else if (c == '"') quoted = true;
            else if (c == sep) { cur.add(sb.toString()); sb.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') i++;
                cur.add(sb.toString()); sb.setLength(0);
                if (!(cur.size() == 1 && cur.get(0).isBlank())) out.add(cur);
                cur = new ArrayList<>();
            } else sb.append(c);
        }
        cur.add(sb.toString());
        if (!(cur.size() == 1 && cur.get(0).isBlank())) out.add(cur);
        return out;
    }
}
