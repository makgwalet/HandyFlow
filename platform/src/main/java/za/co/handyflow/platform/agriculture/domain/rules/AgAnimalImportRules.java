package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Importing animals from a spreadsheet (CSV). The server checks every row for itself: the screen's preview is only a convenience.
 * A row is OK (it will be created), a DUPLICATE (the tag is already on the farm, or earlier in the same file: skipped, never
 * overwritten) or an ERROR (said in plain words, nothing is created for it). One bad row never stops the others.
 * Pure and dependency-free so it can be tested without Spring or a database.
 */
public final class AgAnimalImportRules {

    private AgAnimalImportRules() {}

    public static final int MAX_ROWS = 500;
    public static final int MAX_TAG = 50;
    public static final int MAX_TEXT = 100;
    public static final String OK = "OK";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String ERROR = "ERROR";

    private static final Set<String> SEXES = Set.of("MALE", "FEMALE", "CASTRATED");
    private static final Set<String> ACQUISITION = Set.of("BORN_ON_FARM", "PURCHASED", "TRANSFERRED_IN");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("uuuu-M-d"), DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("uuuu/M/d")};

    /** One line of the file as typed. Everything is text so a bad value becomes a row error, not a failed request. */
    public record Row(String tagNumber, String name, String species, String sex, String breed, String dateOfBirth,
                      String area, String acquisitionType, String acquisitionDate) {}

    public record SpeciesInfo(UUID id, String trackingMode, String status) {}
    public record AreaInfo(UUID id, String status) {}

    /** What the farm already has. Keys are lower-case names; {@code existingTags} are lower-case tags. */
    public record Context(Set<String> existingTags, Map<String, SpeciesInfo> speciesByName, Map<String, AreaInfo> areaByName, LocalDate today) {}

    /** The animal this row would create. */
    public record Parsed(String tagNumber, String name, UUID speciesId, String sex, String breed, LocalDate dateOfBirth,
                         UUID areaId, String acquisitionType, LocalDate acquisitionDate) {}

    public record Result(int line, String tagNumber, String status, String message, Parsed parsed) {}

    public static void requireRowCount(int rows) {
        if (rows == 0) throw new IllegalArgumentException("The file has no rows to import");
        if (rows > MAX_ROWS) throw new IllegalArgumentException("Import at most " + MAX_ROWS + " animals at a time (the file has " + rows + ")");
    }

    /** Checks every row, in order. {@code line} is the row's position (1 for the first animal). */
    public static List<Result> plan(List<Row> rows, Context ctx) {
        requireRowCount(rows == null ? 0 : rows.size());
        Set<String> seen = new HashSet<>();
        List<Result> out = new ArrayList<>();
        int line = 0;
        for (Row r : rows) {
            line++;
            String tag = clean(r.tagNumber());
            if (tag.isEmpty()) { out.add(new Result(line, "", ERROR, "Tag number is required", null)); continue; }
            if (tag.length() > MAX_TAG) { out.add(new Result(line, tag, ERROR, "Tag number is longer than " + MAX_TAG + " characters", null)); continue; }
            String key = tag.toLowerCase(Locale.ROOT);
            if (ctx.existingTags().contains(key)) { out.add(new Result(line, tag, DUPLICATE, "Tag " + tag + " is already on this farm, so it was skipped", null)); continue; }
            if (!seen.add(key)) { out.add(new Result(line, tag, DUPLICATE, "Tag " + tag + " appears earlier in this file, so this row was skipped", null)); continue; }
            out.add(check(line, tag, r, ctx));
        }
        return out;
    }

    private static Result check(int line, String tag, Row r, Context ctx) {
        String name = clean(r.name()), breed = clean(r.breed());
        if (name.length() > MAX_TEXT) return err(line, tag, "Name is longer than " + MAX_TEXT + " characters");
        if (breed.length() > MAX_TEXT) return err(line, tag, "Breed is longer than " + MAX_TEXT + " characters");

        String speciesName = clean(r.species());
        if (speciesName.isEmpty()) return err(line, tag, "Species is required");
        SpeciesInfo species = ctx.speciesByName().get(speciesName.toLowerCase(Locale.ROOT));
        if (species == null) return err(line, tag, "Species '" + speciesName + "' is not in your species list");
        if ("INACTIVE".equals(species.status())) return err(line, tag, "Species '" + speciesName + "' is inactive");
        if ("GROUP".equals(species.trackingMode())) return err(line, tag, "'" + speciesName + "' is tracked as groups, not as individual animals");

        String sex = normalizeSex(r.sex());
        if (sex == null) return err(line, tag, clean(r.sex()).isEmpty() ? "Sex is required" : "Sex '" + clean(r.sex()) + "' must be male, female or castrated");

        LocalDate dob = null;
        if (!clean(r.dateOfBirth()).isEmpty()) {
            dob = parseDate(r.dateOfBirth());
            if (dob == null) return err(line, tag, "Date of birth '" + clean(r.dateOfBirth()) + "' is not a date (use 2026-03-15 or 15/03/2026)");
            if (dob.isAfter(ctx.today())) return err(line, tag, "Date of birth is in the future");
            if (dob.isBefore(ctx.today().minusYears(60))) return err(line, tag, "Date of birth is more than 60 years ago. Check the year");
        }

        UUID areaId = null;
        if (!clean(r.area()).isEmpty()) {
            AreaInfo area = ctx.areaByName().get(clean(r.area()).toLowerCase(Locale.ROOT));
            if (area == null) return err(line, tag, "Production area '" + clean(r.area()) + "' is not on this farm");
            if ("INACTIVE".equals(area.status())) return err(line, tag, "Production area '" + clean(r.area()) + "' is inactive");
            areaId = area.id();
        }

        String acquisition = clean(r.acquisitionType()).isEmpty() ? "PURCHASED" : clean(r.acquisitionType()).toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!ACQUISITION.contains(acquisition)) return err(line, tag, "Acquisition type '" + clean(r.acquisitionType()) + "' must be born on farm, purchased or transferred in");
        LocalDate acquiredOn = ctx.today();
        if (!clean(r.acquisitionDate()).isEmpty()) {
            acquiredOn = parseDate(r.acquisitionDate());
            if (acquiredOn == null) return err(line, tag, "Acquisition date '" + clean(r.acquisitionDate()) + "' is not a date (use 2026-03-15 or 15/03/2026)");
            if (acquiredOn.isAfter(ctx.today())) return err(line, tag, "Acquisition date is in the future");
        } else if ("BORN_ON_FARM".equals(acquisition) && dob != null) {
            acquiredOn = dob;
        }
        if (dob != null && acquiredOn.isBefore(dob)) return err(line, tag, "Acquisition date is before the date of birth");

        return new Result(line, tag, OK, null, new Parsed(tag, name.isEmpty() ? null : name, species.id(), sex,
                breed.isEmpty() ? null : breed, dob, areaId, acquisition, acquiredOn));
    }

    private static Result err(int line, String tag, String message) { return new Result(line, tag, ERROR, message, null); }

    private static String clean(String s) { return s == null ? "" : s.trim(); }

    /** MALE, FEMALE or CASTRATED from what was typed (any case; M, F and the usual words), else null. */
    public static String normalizeSex(String raw) {
        String s = clean(raw).toUpperCase(Locale.ROOT);
        switch (s) {
            case "M": case "BULL": case "RAM": case "BUCK": case "BOAR": case "COCK": case "ROOSTER": return "MALE";
            case "F": case "COW": case "EWE": case "DOE": case "SOW": case "HEN": case "HEIFER": return "FEMALE";
            case "STEER": case "OX": case "WETHER": case "GELDING": return "CASTRATED";
            default: return SEXES.contains(s) ? s : null;
        }
    }

    /** 2026-03-15, 15/03/2026 or 2026/03/15; null for anything else (including a date that does not exist, such as 31/02/2026). */
    public static LocalDate parseDate(String raw) {
        String s = clean(raw);
        if (s.isEmpty()) return null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s, f.withResolverStyle(java.time.format.ResolverStyle.STRICT)); }
            catch (DateTimeParseException ignored) { /* try the next one */ }
        }
        return null;
    }

    public static int count(List<Result> results, String status) {
        int n = 0;
        for (Result r : results) if (status.equals(r.status())) n++;
        return n;
    }
}
