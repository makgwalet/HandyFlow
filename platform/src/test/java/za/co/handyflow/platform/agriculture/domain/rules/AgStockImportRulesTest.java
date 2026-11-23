package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgStockImportRulesTest {

    private static AgStockImportRules.Row row(String name, String cat, String unit, String qty, String cost, String reorder, String supplier, String pack) {
        return new AgStockImportRules.Row(name, cat, unit, qty, cost, reorder, supplier, pack);
    }

    private static AgStockImportRules.Result one(AgStockImportRules.Row r, String... existing) {
        return AgStockImportRules.plan(List.of(r), Set.of(existing)).get(0);
    }

    @Test void aCompleteRowIsReady() {
        var r = one(row("Layer mash", "feed", "Bags", "40", "R 0", "10", "Epol", "50"));
        assertEquals("ERROR", r.status(), "R 0 is not a number");
        r = one(row("Layer mash", "feed", "Bags", "40", "285,50", "10", "Epol", "50"));
        assertEquals("OK", r.status(), r.message());
        var p = r.parsed();
        assertEquals("FEED", p.category());
        assertEquals("bags", p.unit());
        assertEquals(0, new BigDecimal("40").compareTo(p.openingQuantity()));
        assertEquals(new BigDecimal("285.50"), p.unitCost());
        assertEquals(0, new BigDecimal("10").compareTo(p.reorderLevel()));
        assertEquals("Epol", p.supplier());
        assertEquals(0, new BigDecimal("50").compareTo(p.packSizeKg()));
    }

    @Test void onlyNameCategoryAndUnitAreRequired() {
        var r = one(row("Lucerne", "Feed", "bales", "", "", "", "", ""));
        assertEquals("OK", r.status(), r.message());
        assertEquals(0, r.parsed().openingQuantity().signum());
        assertNull(r.parsed().unitCost());
        assertNull(r.parsed().reorderLevel());
        assertNull(r.parsed().supplier());
        assertNull(r.parsed().packSizeKg());
    }

    @Test void missingRequiredFieldsAreErrorsWithTheReason() {
        assertEquals("Item name is required", one(row(" ", "feed", "kg", "", "", "", "", "")).message());
        assertTrue(one(row("A", "", "kg", "", "", "", "", "")).message().startsWith("Category is required"));
        assertTrue(one(row("A", "feed", "", "", "", "", "", "")).message().startsWith("Unit is required"));
    }

    @Test void unknownCategoryAndUnitAreErrors() {
        var c = one(row("A", "snacks", "kg", "", "", "", "", ""));
        assertEquals("ERROR", c.status());
        assertTrue(c.message().contains("'snacks'"));
        var u = one(row("A", "feed", "pallets", "", "", "", "", ""));
        assertEquals("ERROR", u.status());
        assertTrue(u.message().contains("'pallets'"));
    }

    @Test void unitSpellingsAreNormalised() {
        assertEquals("kg", AgStockImportRules.normalizeUnit(" KGS "));
        assertEquals("L", AgStockImportRules.normalizeUnit("litres"));
        assertEquals("L", AgStockImportRules.normalizeUnit("l"));
        assertEquals("L", AgStockImportRules.normalizeUnit("L"));
        assertEquals("mL", AgStockImportRules.normalizeUnit("ml"));
        assertEquals("t", AgStockImportRules.normalizeUnit("Tonnes"));
        assertEquals("bags", AgStockImportRules.normalizeUnit("bag"));
        assertEquals("bales", AgStockImportRules.normalizeUnit("Bale"));
        assertNull(AgStockImportRules.normalizeUnit("pallet"));
        assertNull(AgStockImportRules.normalizeUnit(null));
    }

    @Test void categoryWithSpacesOrCaseIsAccepted() {
        assertEquals("VETERINARY", one(row("Ivomec", " veterinary ", "mL", "", "", "", "", "")).parsed().category());
    }

    @Test void numbersAcceptACommaOrADotAsTheDecimalMark() {
        assertEquals(new BigDecimal("12.5"), AgStockImportRules.parseNumber("12,5"));
        assertEquals(new BigDecimal("12.5"), AgStockImportRules.parseNumber("12.5"));
        assertEquals(new BigDecimal("1234.5"), AgStockImportRules.parseNumber("1,234.5"));
        assertEquals(new BigDecimal("1234.5"), AgStockImportRules.parseNumber("1 234,5"));
        assertNull(AgStockImportRules.parseNumber("1,234,5"));
        assertNull(AgStockImportRules.parseNumber("abc"));
        assertNull(AgStockImportRules.parseNumber("12kg"));
        assertNull(AgStockImportRules.parseNumber(""));
    }

    @Test void badNumbersAreErrorsNamingTheColumn() {
        assertTrue(one(row("A", "feed", "kg", "lots", "", "", "", "")).message().startsWith("Quantity 'lots'"));
        assertTrue(one(row("A", "feed", "kg", "", "cheap", "", "", "")).message().startsWith("Unit cost 'cheap'"));
        assertTrue(one(row("A", "feed", "kg", "", "", "few", "", "")).message().startsWith("Reorder level 'few'"));
        assertTrue(one(row("A", "feed", "bags", "", "", "", "", "heavy")).message().startsWith("Kg per bag 'heavy'"));
    }

    @Test void negativeNumbersAreErrors() {
        assertEquals("Quantity cannot be negative", one(row("A", "feed", "kg", "-1", "", "", "", "")).message());
        assertEquals("Unit cost cannot be negative", one(row("A", "feed", "kg", "", "-1", "", "", "")).message());
        assertEquals("Reorder level cannot be negative", one(row("A", "feed", "kg", "", "", "-1", "", "")).message());
    }

    @Test void absurdNumbersAreErrors() {
        assertTrue(one(row("A", "feed", "kg", "1000000001", "", "", "", "")).message().contains("not credible"));
        assertTrue(one(row("A", "feed", "kg", "", "1000000001", "", "", "")).message().contains("not credible"));
        assertTrue(one(row("A", "feed", "kg", "", "", "1000000001", "", "")).message().contains("not credible"));
        assertEquals("OK", one(row("A", "feed", "kg", "1000000000", "", "", "", "")).status());
    }

    @Test void kgPerBagOnlyForBagsAndTheLikeAndWithinTheLimit() {
        assertEquals("OK", one(row("A", "feed", "bags", "", "", "", "", "50")).status());
        assertEquals("ERROR", one(row("A", "feed", "kg", "", "", "", "", "50")).status());
        assertEquals("ERROR", one(row("A", "feed", "bags", "", "", "", "", "0")).status());
        assertEquals("ERROR", one(row("A", "feed", "bags", "", "", "", "", "10001")).status());
    }

    @Test void aNameAlreadyOnTheFarmIsSkippedIgnoringCase() {
        var r = one(row("  Layer MASH ", "feed", "kg", "", "", "", "", ""), "layer mash");
        assertEquals("DUPLICATE", r.status());
        assertNull(r.parsed());
        assertTrue(r.message().contains("already in this farm's stock"));
    }

    @Test void aNameRepeatedInTheFileIsSkippedAfterTheFirst() {
        var results = AgStockImportRules.plan(List.of(row("Salt lick", "feed", "units", "", "", "", "", ""), row("SALT LICK", "feed", "units", "", "", "", "", "")), Set.of());
        assertEquals("OK", results.get(0).status());
        assertEquals("DUPLICATE", results.get(1).status());
        assertTrue(results.get(1).message().contains("earlier in this file"));
        assertEquals(2, results.get(1).line());
    }

    @Test void aBadRowDoesNotStopTheOthersAndDoesNotReserveItsName() {
        var results = AgStockImportRules.plan(List.of(row("Maize", "feed", "pallets", "", "", "", "", ""), row("Maize", "feed", "kg", "", "", "", "", "")), Set.of());
        assertEquals("ERROR", results.get(0).status());
        assertEquals("OK", results.get(1).status());
        assertEquals(1, AgStockImportRules.count(results, "OK"));
        assertEquals(1, AgStockImportRules.count(results, "ERROR"));
    }

    @Test void longNamesAndSuppliersAreRejected() {
        assertTrue(one(row("x".repeat(256), "feed", "kg", "", "", "", "", "")).message().contains("longer than 255"));
        assertEquals("OK", one(row("x".repeat(255), "feed", "kg", "", "", "", "", "")).status());
        assertTrue(one(row("A", "feed", "kg", "", "", "", "y".repeat(101), "")).message().contains("Supplier"));
        assertEquals("OK", one(row("A", "feed", "kg", "", "", "", "y".repeat(100), "")).status());
    }

    @Test void rowLimitsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> AgStockImportRules.plan(List.of(), Set.of()));
        List<AgStockImportRules.Row> many = new ArrayList<>();
        for (int i = 0; i < 501; i++) many.add(row("Item " + i, "feed", "kg", "", "", "", "", ""));
        var e = assertThrows(IllegalArgumentException.class, () -> AgStockImportRules.plan(many, Set.of()));
        assertTrue(e.getMessage().contains("501"));
        assertEquals(500, AgStockImportRules.plan(many.subList(0, 500), Set.of()).size());
    }

    @Test void costIsRoundedToCentsAndQuantityToThreeDecimals() {
        var p = one(row("A", "feed", "kg", "1.23456", "9.999", "", "", "")).parsed();
        assertEquals(new BigDecimal("10.00"), p.unitCost());
        assertEquals(new BigDecimal("1.235"), p.openingQuantity());
    }
}
