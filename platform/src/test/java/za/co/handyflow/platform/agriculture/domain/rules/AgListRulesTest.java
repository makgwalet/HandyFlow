package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgListRulesTest {

    @Test void blankSearchMatchesEverything() {
        assertEquals("%", AgListRules.likePattern(null));
        assertEquals("%", AgListRules.likePattern(""));
        assertEquals("%", AgListRules.likePattern("   "));
    }

    @Test void searchIsContainsAndNotCaseSensitive() {
        assertEquals("%ab 12%", AgListRules.likePattern("  AB 12 "));
    }

    @Test void wildcardsTypedByThePersonAreEscaped() {
        assertEquals("%50!%%", AgListRules.likePattern("50%"));
        assertEquals("%a!_b%", AgListRules.likePattern("a_b"));
        assertEquals("%a!!b%", AgListRules.likePattern("a!b"));
        assertEquals("%!%!_!!%", AgListRules.likePattern("%_!"));
    }

    @Test void searchLengthLimitIsInclusive() {
        assertEquals("%" + "a".repeat(60) + "%", AgListRules.likePattern("a".repeat(60)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AgListRules.likePattern("a".repeat(61)));
        assertTrue(e.getMessage().contains("60"), e.getMessage());
    }

    @Test void controlCharactersInTheSearchAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgListRules.likePattern("a\nb"));
        assertThrows(IllegalArgumentException.class, () -> AgListRules.likePattern("a\u0000b"));
    }

    @Test void statusIsTrimmedUpperCasedAndEmptyMeansAny() {
        assertEquals("", AgListRules.status(null));
        assertEquals("", AgListRules.status("  "));
        assertEquals("ACTIVE", AgListRules.status(" active "));
        assertEquals("SOLD", AgListRules.status("SOLD"));
    }

    @Test void pageIndexIsNeverNegative() {
        assertEquals(0, AgListRules.pageIndex(-4));
        assertEquals(0, AgListRules.pageIndex(0));
        assertEquals(7, AgListRules.pageIndex(7));
    }

    @Test void pageSizeIsClampedToOneToTwoHundred() {
        assertEquals(50, AgListRules.pageSize(0));
        assertEquals(50, AgListRules.pageSize(-1));
        assertEquals(1, AgListRules.pageSize(1));
        assertEquals(200, AgListRules.pageSize(200));
        assertEquals(200, AgListRules.pageSize(201));
        assertEquals(200, AgListRules.pageSize(100000));
        assertEquals(75, AgListRules.pageSize(75));
    }

    @Test void rowRangeIsOneBasedAndStopsAtTheTotal() {
        assertArrayEquals(new int[] {1, 50}, AgListRules.rowRange(0, 50, 120));
        assertArrayEquals(new int[] {51, 100}, AgListRules.rowRange(1, 50, 120));
        assertArrayEquals(new int[] {101, 120}, AgListRules.rowRange(2, 50, 120));
        assertArrayEquals(new int[] {1, 3}, AgListRules.rowRange(0, 50, 3));
        assertArrayEquals(new int[] {0, 0}, AgListRules.rowRange(0, 50, 0));
        assertArrayEquals(new int[] {0, 0}, AgListRules.rowRange(5, 50, 120), "a page past the end shows nothing");
        assertArrayEquals(new int[] {1, 50}, AgListRules.rowRange(0, 50, 50));
        assertArrayEquals(new int[] {51, 51}, AgListRules.rowRange(1, 50, 51), "one row on the last page");
        assertArrayEquals(new int[] {1, 1}, AgListRules.rowRange(0, 50, 1));
    }
}
