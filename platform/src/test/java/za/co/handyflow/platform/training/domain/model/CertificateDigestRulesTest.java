package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.training.domain.model.CertificateDigestRules.Item;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertificateDigestRulesTest {

    private static Item item(String person, long days) { return new Item(person, "First Aid", LocalDate.of(2026, 11, 11), days); }

    @Test
    void titlesAgreeWithTheCount() {
        assertEquals("A certificate in your team is about to expire", CertificateDigestRules.title(false, 1));
        assertEquals("3 certificates in your team are about to expire", CertificateDigestRules.title(false, 3));
        assertEquals("A certificate in your team has expired", CertificateDigestRules.title(true, 1));
        assertEquals("2 certificates in your team have expired", CertificateDigestRules.title(true, 2));
    }

    @Test
    void listsEachPersonWithTheirDate() {
        String m = CertificateDigestRules.message(false, List.of(item("Thandi Nkosi", 14), item("Sipho Dube", 1)));
        assertTrue(m.contains("Thandi Nkosi: First Aid expires in 14 days (2026-11-11)."));
        assertTrue(m.contains("Sipho Dube: First Aid expires tomorrow (2026-11-11)."));
        assertTrue(CertificateDigestRules.message(true, List.of(item("Thandi Nkosi", -1))).contains("expired on 2026-11-11."));
    }

    @Test
    void cutsALongListShort() {
        List<Item> many = new ArrayList<>();
        for (int i = 0; i < 14; i++) many.add(item("Person " + i, 7));
        String m = CertificateDigestRules.message(false, many);
        assertTrue(m.contains("Person 9"));
        assertFalse(m.contains("Person 10"));
        assertTrue(m.contains("And 4 more."));
    }
}
