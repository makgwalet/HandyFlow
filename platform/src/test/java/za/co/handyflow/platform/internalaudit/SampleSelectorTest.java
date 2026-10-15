package za.co.handyflow.platform.internalaudit;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.internalaudit.application.internal.SampleSelector;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class SampleSelectorTest {

    @Test
    void sameSeedGivesSameSample() {
        var a = SampleSelector.select(500, 25, "RANDOM", 42L);
        var b = SampleSelector.select(500, 25, "RANDOM", 42L);
        assertEquals(a.positions(), b.positions());
    }

    @Test
    void differentSeedUsuallyDiffers() {
        assertNotEquals(SampleSelector.select(500, 25, "RANDOM", 1L).positions(),
                SampleSelector.select(500, 25, "RANDOM", 2L).positions());
    }

    @Test
    void randomPicksDistinctPositionsInRange() {
        var d = SampleSelector.select(100, 30, "RANDOM", 7L);
        assertEquals(30, d.positions().size());
        assertEquals(30, new HashSet<>(d.positions()).size());
        assertTrue(d.positions().stream().allMatch(p -> p >= 0 && p < 100));
        assertNull(d.start());
    }

    @Test
    void systematicUsesFixedIntervalFromRandomStart() {
        var d = SampleSelector.select(100, 10, "SYSTEMATIC", 9L);
        assertEquals(10, d.positions().size());
        assertTrue(d.start() >= 0 && d.start() < 10);
        for (int i = 1; i < 10; i++) assertEquals(10, d.positions().get(i) - d.positions().get(i - 1));
        assertTrue(d.positions().get(9) < 100);
    }

    @Test
    void sampleAsLargeAsPopulationTakesEverything() {
        assertEquals(5, SampleSelector.select(5, 5, "SYSTEMATIC", 3L).positions().size());
        assertEquals(5, SampleSelector.select(5, 5, "RANDOM", 3L).positions().size());
    }

    @Test
    void rejectsBadSizesAndMethods() {
        assertThrows(IllegalArgumentException.class, () -> SampleSelector.select(10, 0, "RANDOM", 1L));
        assertThrows(IllegalArgumentException.class, () -> SampleSelector.select(10, 11, "RANDOM", 1L));
        assertThrows(IllegalArgumentException.class, () -> SampleSelector.select(10, 5, "JUDGMENTAL", 1L));
    }
}
