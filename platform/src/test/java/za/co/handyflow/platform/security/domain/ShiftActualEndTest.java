package za.co.handyflow.platform.security.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Reads the source: the entity can't be built without the persistence stack, and what matters is which paths record the end. */
class ShiftActualEndTest {

    private static String read(String rel) throws Exception {
        for (String root : new String[]{"src/main", "platform/src/main", "work/src/main"}) {
            Path p = Path.of(root, rel);
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing " + rel); return "";
    }

    private static String method(String src, String signature) {
        int i = src.indexOf(signature);
        assertTrue(i >= 0, "no " + signature);
        int next = src.indexOf("\n    public ", i + signature.length());
        return src.substring(i, next < 0 ? src.length() : next);
    }

    private static final String SHIFT = "java/za/co/handyflow/platform/security/domain/model/Shift.java";

    @Test
    void theGuardsOwnClockOutRecordsTheEnd() throws Exception {
        assertTrue(method(read(SHIFT), "public void complete()").contains("this.actualEndAt = Instant.now();"));
    }

    @Test
    void aForceCloseOrPullDoesNotInventAnEnd() throws Exception {
        String s = read(SHIFT);
        assertFalse(method(s, "public void closeOvertime(").contains("actualEndAt"));
        assertFalse(method(s, "public void start()").contains("actualEndAt"));
        int pull = s.indexOf("public void pull");
        assertTrue(pull > 0);
        assertFalse(method(s, "public void pull").contains("actualEndAt"));
    }

    @Test
    void theColumnIsMappedAndMigrated() throws Exception {
        assertTrue(read(SHIFT).contains("@Column(name = \"actual_end_at\")"));
        String m = read("resources/db/migration/V858__security_shift_actual_end.sql");
        assertTrue(m.contains("ADD COLUMN IF NOT EXISTS actual_end_at TIMESTAMP;"));
        assertFalse(m.toUpperCase().contains("NOT NULL"));
    }
}
