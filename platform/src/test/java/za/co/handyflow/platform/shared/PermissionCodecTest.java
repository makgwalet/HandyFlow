package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PermissionCodecTest {

    @Test void roundTripsAndIgnoresOrder() {
        Set<String> in = Set.of("CLINIC_READ", "CLINIC_TASK_READ", "INVOICE_READ", "HR_PAYROLL_WRITE");
        assertEquals(in, PermissionCodec.decode(PermissionCodec.encode(in)));
    }

    @Test void emptyAndNullAreEmpty() {
        assertEquals("", PermissionCodec.encode(Set.of()));
        assertEquals("", PermissionCodec.encode(null));
        assertTrue(PermissionCodec.decode("").isEmpty());
        assertTrue(PermissionCodec.decode(null).isEmpty());
    }

    @Test void aLargeCataloguePacksIntoWellUnderTheHeaderLimit() {
        Set<String> in = new HashSet<>();
        String[] groups = {"PATIENT", "NOTES", "CONSULTATION", "NURSE", "PRESCRIPTION", "RESULT", "BILL", "CLAIM", "APPOINTMENT", "CONTENT", "TASK", "GROWTH", "DOCUMENT", "AUDIT"};
        String[] verbs = {"READ", "CREATE", "UPDATE", "DELETE", "SIGN", "VOID", "EXPORT", "MANAGE", "APPROVE"};
        for (String m : new String[]{"CLINIC", "HR", "ACCOUNTING", "INVENTORY", "CRM"})
            for (String g : groups) for (String v : verbs) in.add(m + "_" + g + "_" + v);
        int plain = String.join(",", in).length();
        String packed = PermissionCodec.encode(in);
        assertEquals(in, PermissionCodec.decode(packed));
        assertTrue(packed.length() < plain / 2, "packed " + packed.length() + " vs plain " + plain);
        assertTrue(packed.length() < 3000);
    }

    @Test void garbageIsRejectedNotTrusted() {
        assertThrows(IllegalArgumentException.class, () -> PermissionCodec.decode("!!!not base64!!!"));
        assertThrows(IllegalArgumentException.class, () -> PermissionCodec.decode("AAAA"));
    }
}
