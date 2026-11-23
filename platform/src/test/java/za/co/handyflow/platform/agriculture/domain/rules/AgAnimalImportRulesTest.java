package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgAnimalImportRulesTest {

    private static final UUID COW = UUID.randomUUID(), CHICKEN = UUID.randomUUID(), CAMP = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    private static AgAnimalImportRules.Context ctx(String... existingTags) {
        return new AgAnimalImportRules.Context(Set.of(existingTags),
                Map.of("cattle", new AgAnimalImportRules.SpeciesInfo(COW, "BOTH", "ACTIVE"),
                        "broilers", new AgAnimalImportRules.SpeciesInfo(CHICKEN, "GROUP", "ACTIVE"),
                        "pigs", new AgAnimalImportRules.SpeciesInfo(UUID.randomUUID(), "INDIVIDUAL", "INACTIVE")),
                Map.of("north camp", new AgAnimalImportRules.AreaInfo(CAMP, "ACTIVE"), "old camp", new AgAnimalImportRules.AreaInfo(UUID.randomUUID(), "INACTIVE")),
                TODAY);
    }

    private static AgAnimalImportRules.Row row(String tag, String species, String sex) {
        return new AgAnimalImportRules.Row(tag, null, species, sex, null, null, null, null, null);
    }

    private static AgAnimalImportRules.Result one(AgAnimalImportRules.Row r, AgAnimalImportRules.Context c) { return AgAnimalImportRules.plan(List.of(r), c).get(0); }

    @Test void aGoodRowIsReadyAndFilledWithDefaults() {
        var r = one(row(" 1001 ", "Cattle", "f"), ctx());
        assertEquals("OK", r.status());
        assertEquals("1001", r.parsed().tagNumber());
        assertEquals(COW, r.parsed().speciesId());
        assertEquals("FEMALE", r.parsed().sex());
        assertEquals("PURCHASED", r.parsed().acquisitionType());
        assertEquals(TODAY, r.parsed().acquisitionDate());
        assertNull(r.parsed().areaId());
    }

    @Test void theWholeRowIsUsedWhenGiven() {
        var r = one(new AgAnimalImportRules.Row("A7", "Daisy", "CATTLE", "Female", "Nguni", "15/03/2024", "North Camp", "born on farm", null), ctx());
        assertEquals("OK", r.status(), r.message());
        assertEquals("Daisy", r.parsed().name());
        assertEquals("Nguni", r.parsed().breed());
        assertEquals(LocalDate.of(2024, 3, 15), r.parsed().dateOfBirth());
        assertEquals(CAMP, r.parsed().areaId());
        assertEquals("BORN_ON_FARM", r.parsed().acquisitionType());
        assertEquals(LocalDate.of(2024, 3, 15), r.parsed().acquisitionDate(), "an animal born on the farm was acquired the day it was born");
    }

    @Test void aTagAlreadyOnTheFarmIsSkippedNotOverwritten() {
        var r = one(row("A001", "Cattle", "MALE"), ctx("a001"));
        assertEquals("DUPLICATE", r.status());
        assertTrue(r.message().contains("already on this farm"));
        assertNull(r.parsed());
    }

    @Test void aTagRepeatedInTheFileKeepsTheFirstOnly() {
        var out = AgAnimalImportRules.plan(List.of(row("T1", "Cattle", "MALE"), row("t1", "Cattle", "MALE"), row("T2", "Cattle", "MALE")), ctx());
        assertEquals(List.of("OK", "DUPLICATE", "OK"), out.stream().map(AgAnimalImportRules.Result::status).toList());
        assertTrue(out.get(1).message().contains("earlier in this file"));
        assertEquals(List.of(1, 2, 3), out.stream().map(AgAnimalImportRules.Result::line).toList());
    }

    @Test void aRowThatFailsDoesNotUseUpItsTagOrStopTheOthers() {
        var out = AgAnimalImportRules.plan(List.of(row("X1", "Unicorn", "MALE"), row("X1", "Cattle", "MALE")), ctx());
        assertEquals("ERROR", out.get(0).status());
        assertEquals("DUPLICATE", out.get(1).status(), "the tag was seen even though the first row failed, so the file cannot give one tag two animals");
    }

    @Test void problemsAreSaidInPlainWords() {
        assertEquals("Tag number is required", one(row(" ", "Cattle", "MALE"), ctx()).message());
        assertEquals("Species is required", one(row("1", "", "MALE"), ctx()).message());
        assertEquals("Species 'Unicorn' is not in your species list", one(row("1", "Unicorn", "MALE"), ctx()).message());
        assertTrue(one(row("1", "Pigs", "MALE"), ctx()).message().contains("inactive"));
        assertTrue(one(row("1", "Broilers", "MALE"), ctx()).message().contains("tracked as groups"));
        assertEquals("Sex is required", one(row("1", "Cattle", ""), ctx()).message());
        assertTrue(one(row("1", "Cattle", "purple"), ctx()).message().contains("must be male, female or castrated"));
    }

    @Test void datesAreReadInSouthAfricanOrIsoFormAndMustBeSensible() {
        assertEquals(LocalDate.of(2026, 3, 15), AgAnimalImportRules.parseDate("2026-03-15"));
        assertEquals(LocalDate.of(2026, 3, 5), AgAnimalImportRules.parseDate("5/3/2026"));
        assertEquals(LocalDate.of(2026, 3, 15), AgAnimalImportRules.parseDate("2026/03/15"));
        assertNull(AgAnimalImportRules.parseDate("31/02/2026"));
        assertNull(AgAnimalImportRules.parseDate("15-03-26"));
        assertNull(AgAnimalImportRules.parseDate("soon"));
        assertNull(AgAnimalImportRules.parseDate(" "));
        var base = new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, "2030-01-01", null, null, null);
        assertEquals("Date of birth is in the future", one(base, ctx()).message());
        assertTrue(one(new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, "1950-01-01", null, null, null), ctx()).message().contains("60 years"));
        assertTrue(one(new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, "x", null, null, null), ctx()).message().contains("not a date"));
    }

    @Test void areaAndAcquisitionAreChecked() {
        assertTrue(one(new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, null, "Nowhere", null, null), ctx()).message().contains("not on this farm"));
        assertTrue(one(new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, null, "Old Camp", null, null), ctx()).message().contains("inactive"));
        assertTrue(one(new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, null, null, "stolen", null), ctx()).message().contains("must be born on farm"));
        assertEquals("Acquisition date is in the future", one(new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, null, null, null, "2027-01-01"), ctx()).message());
        assertEquals("Acquisition date is before the date of birth",
                one(new AgAnimalImportRules.Row("1", null, "Cattle", "MALE", null, "2025-05-01", null, "PURCHASED", "2025-04-01"), ctx()).message());
    }

    @Test void lengthsAreLimited() {
        assertTrue(one(row("x".repeat(51), "Cattle", "MALE"), ctx()).message().contains("longer than 50"));
        assertTrue(one(new AgAnimalImportRules.Row("1", "n".repeat(101), "Cattle", "MALE", null, null, null, null, null), ctx()).message().contains("Name is longer"));
        assertTrue(one(row("x".repeat(50), "Cattle", "MALE"), ctx()).status().equals("OK"));
    }

    @Test void sexWordsAreUnderstood() {
        assertEquals("MALE", AgAnimalImportRules.normalizeSex("bull"));
        assertEquals("MALE", AgAnimalImportRules.normalizeSex(" M "));
        assertEquals("FEMALE", AgAnimalImportRules.normalizeSex("Heifer"));
        assertEquals("CASTRATED", AgAnimalImportRules.normalizeSex("steer"));
        assertEquals("CASTRATED", AgAnimalImportRules.normalizeSex("castrated"));
        assertNull(AgAnimalImportRules.normalizeSex("x"));
        assertNull(AgAnimalImportRules.normalizeSex(null));
    }

    @Test void theRowCountIsLimited() {
        assertThrows(IllegalArgumentException.class, () -> AgAnimalImportRules.plan(List.of(), ctx()));
        assertThrows(IllegalArgumentException.class, () -> AgAnimalImportRules.plan(null, ctx()));
        AgAnimalImportRules.requireRowCount(500);
        assertThrows(IllegalArgumentException.class, () -> AgAnimalImportRules.requireRowCount(501));
    }

    @Test void countsByStatus() {
        var out = AgAnimalImportRules.plan(List.of(row("1", "Cattle", "MALE"), row("1", "Cattle", "MALE"), row("2", "Nope", "MALE"), row("3", "Cattle", "FEMALE")), ctx());
        assertEquals(2, AgAnimalImportRules.count(out, "OK"));
        assertEquals(1, AgAnimalImportRules.count(out, "DUPLICATE"));
        assertEquals(1, AgAnimalImportRules.count(out, "ERROR"));
    }
}
