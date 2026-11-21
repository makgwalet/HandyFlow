package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BulkAccessRulesTest {
    @Test void normalizeDedupesAndDropsNulls() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<UUID> in = new ArrayList<>(); in.add(a); in.add(null); in.add(b); in.add(a);
        assertEquals(List.of(a, b), BulkAccessRules.normalizeIds(in));
        assertEquals(List.of(), BulkAccessRules.normalizeIds(null));
    }
    @Test void capIsEnforcedAfterDedupe() {
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < BulkAccessRules.MAX_PER_REQUEST; i++) many.add(UUID.randomUUID());
        assertEquals(BulkAccessRules.MAX_PER_REQUEST, BulkAccessRules.normalizeIds(many).size());
        many.add(UUID.randomUUID());
        boolean threw = false;
        try { BulkAccessRules.normalizeIds(many); } catch (IllegalArgumentException e) { threw = true; }
        assertTrue(threw, "over cap must be rejected");
    }
    @Test void classify() {
        assertEquals(BulkAccessRules.Eligibility.PROVISION, BulkAccessRules.classify("ACTIVE", false, false));
        assertEquals(BulkAccessRules.Eligibility.SKIP_ALREADY_ENROLLED, BulkAccessRules.classify("ACTIVE", true, false));
        assertEquals(BulkAccessRules.Eligibility.PROVISION, BulkAccessRules.classify("ACTIVE", true, true));
        assertEquals(BulkAccessRules.Eligibility.SKIP_NOT_ACTIVE, BulkAccessRules.classify("SUSPENDED", false, true));
        assertEquals(BulkAccessRules.Eligibility.SKIP_NOT_ACTIVE, BulkAccessRules.classify("TERMINATED", false, false));
        assertEquals(BulkAccessRules.Eligibility.SKIP_NOT_ACTIVE, BulkAccessRules.classify(null, false, false));
    }
    @Test void csvEscapingAndFormulaGuard() {
        assertEquals("plain", BulkAccessRules.csvField("plain"));
        assertEquals("", BulkAccessRules.csvField(null));
        assertEquals("\"a,b\"", BulkAccessRules.csvField("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", BulkAccessRules.csvField("say \"hi\""));
        assertEquals("'=SUM(A1)", BulkAccessRules.csvField("=SUM(A1)"));
        assertEquals("'@cmd", BulkAccessRules.csvField("@cmd"));
        assertEquals("'+27821234567", BulkAccessRules.csvField("+27821234567"));
        assertEquals("a,b\r\n", BulkAccessRules.csvLine("a", "b"));
    }
}
