package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseFloorLayout.Slot;
import za.co.handyflow.platform.warehousing.application.internal.WhseFloorLayout.Spec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhseFloorLayoutTest {

    private static Spec spec(List<String> aisles, int bays, int levels) {
        return new Spec("Zone A", aisles, bays, levels, "PALLET", 2, 1, 2);
    }

    @Test @DisplayName("generates aisles x bays x levels with codes and floor cells")
    void generates() {
        List<Slot> s = WhseFloorLayout.generate(spec(List.of("a", "b"), 3, 2), 1);
        assertEquals(12, s.size());
        Slot first = s.get(0);
        assertEquals("A-01-01", first.code());
        assertEquals(2, first.x());
        assertEquals(1, first.y());
        assertEquals("Zone A", first.zone());
        Slot lastOfA = s.get(5);
        assertEquals("A-03-02", lastOfA.code());
        assertEquals(4, lastOfA.x());
        Slot firstOfB = s.get(6);
        assertEquals("B-03-01", firstOfB.code());
        assertEquals(3, firstOfB.y(), "the second aisle is two rows down");
    }

    @Test @DisplayName("levels of one bay share a cell")
    void levelsShareCell() {
        List<Slot> s = WhseFloorLayout.generate(spec(List.of("A"), 2, 3), 1);
        assertEquals(s.get(0).x(), s.get(2).x());
        assertEquals(s.get(0).y(), s.get(2).y());
        assertNotEquals(s.get(0).x(), s.get(3).x());
    }

    @Test @DisplayName("the walking order snakes and is numbered from the start value")
    void snakes() {
        List<Slot> s = WhseFloorLayout.generate(spec(List.of("A", "B"), 2, 1), 10);
        assertEquals(List.of("A-01-01", "A-02-01", "B-02-01", "B-01-01"), s.stream().map(Slot::code).toList());
        assertEquals(List.of(10, 11, 12, 13), s.stream().map(Slot::pickSequence).toList());
    }

    @Test @DisplayName("bad specs are refused")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(spec(List.of(), 3, 1), 1));
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(spec(List.of("A"), 0, 1), 1));
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(spec(List.of("A"), 3, 11), 1));
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(spec(List.of("A", "a"), 3, 1), 1));
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(spec(List.of("A-1"), 3, 1), 1));
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(new Spec(null, List.of("A"), 3, 1, "SILO", 0, 0, 2), 1));
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(new Spec(null, List.of("A"), 3, 1, "BIN", -1, 0, 2), 1));
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(new Spec(null, List.of("A"), 3, 1, "BIN", 0, 0, 0), 1));
    }

    @Test @DisplayName("more than 2000 locations in one go is refused")
    void tooMany() {
        List<String> aisles = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) aisles.add("A" + i);
        assertThrows(IllegalArgumentException.class, () -> WhseFloorLayout.generate(spec(aisles, 99, 1), 1));
        assertEquals(1980, WhseFloorLayout.generate(spec(aisles.subList(0, 20), 99, 1), 1).size());
    }

    @Test @DisplayName("a blank zone becomes no zone")
    void blankZone() {
        assertNull(WhseFloorLayout.generate(new Spec("  ", List.of("A"), 1, 1, "BIN", 0, 0, 2), 1).get(0).zone());
    }
}
