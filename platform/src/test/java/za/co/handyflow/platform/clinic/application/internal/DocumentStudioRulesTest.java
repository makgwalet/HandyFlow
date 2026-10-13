package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.LetterheadRules.Row;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStudioRulesTest {

    // --- numbers
    @Test void numbersCarryTypeYearAndSequence() {
        assertEquals("LT-2026-000042", DocumentNumberRules.format("LETTER", 2026, 42));
        assertEquals("SN-2027-000001", DocumentNumberRules.format("SICK_NOTE", 2027, 1));
        assertEquals("RX-2026-123456", DocumentNumberRules.format("PRESCRIPTION", 2026, 123456));
    }
    @Test void aBadTypeOrSequenceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> DocumentNumberRules.format("MYSTERY", 2026, 1));
        assertThrows(IllegalArgumentException.class, () -> DocumentNumberRules.format("LETTER", 2026, 0));
        assertFalse(DocumentNumberRules.known(null));
        assertEquals("Document", DocumentNumberRules.label("MYSTERY"));
    }
    @Test void theFooterNamesTheNumberAndWhereToCheckIt() {
        String f = DocumentNumberRules.footer("LT-2026-000042", "ABCD-EFGH-JKMN-PQRS", "https://app.example.co.za/verify-document");
        assertEquals("Document LT-2026-000042 · Verify code ABCD-EFGH-JKMN-PQRS at app.example.co.za/verify-document", f);
        assertEquals("Document LT-2026-000042", DocumentNumberRules.footer("LT-2026-000042", null, null));
    }

    // --- hashes and codes
    @Test void sha256MatchesTheKnownValue() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", DocumentIntegrityRules.sha256(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DocumentIntegrityRules.sha256("abc"));
    }
    @Test void aCodeHasTheShapeAndLeavesOutLookAlikes() {
        for (int i = 0; i < 200; i++) {
            String c = DocumentIntegrityRules.newCode();
            assertTrue(c.matches("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}"), c);
            assertFalse(c.matches(".*[01OIL].*"), c);
        }
        assertEquals(DocumentIntegrityRules.newCode(new Random(7)), DocumentIntegrityRules.newCode(new Random(7)));
    }
    @Test void codesAreUnlikelyToRepeat() {
        var seen = new java.util.HashSet<String>();
        for (int i = 0; i < 2000; i++) assertTrue(seen.add(DocumentIntegrityRules.newCode()));
    }
    @Test void aTypedCodeIsNormalisedBeforeItIsHashed() {
        String c = "ABCD-EFGH-JKMN-PQRS";
        assertEquals("ABCDEFGHJKMNPQRS", DocumentIntegrityRules.normalise(c));
        assertEquals(DocumentIntegrityRules.codeHash(c), DocumentIntegrityRules.codeHash(" abcd efgh jkmn pqrs "));
        assertEquals(DocumentIntegrityRules.codeHash(c), DocumentIntegrityRules.codeHash("abcdefghjkmnpqrs"));
    }
    @Test void somethingThatIsNotACodeHasNoHash() {
        assertNull(DocumentIntegrityRules.codeHash(null));
        assertNull(DocumentIntegrityRules.codeHash("short"));
        assertNull(DocumentIntegrityRules.codeHash("ABCD-EFGH-JKMN-PQR0"));   // 0 is not in the alphabet
        assertNull(DocumentIntegrityRules.codeHash("ABCD-EFGH-JKMN-PQRST"));  // too long
    }

    // --- letterheads
    private static Row row(String name, String branch, boolean def, boolean active) {
        return new Row(UUID.randomUUID(), name, branch, "Sample Practice", List.of("1 Road"), def, active);
    }

    @Test void theChosenLetterheadWinsIfItIsActive() {
        Row a = row("Main", null, true, true), b = row("North", "North", false, true);
        assertSame(b, LetterheadRules.pick(List.of(a, b), b.id(), null));
    }
    @Test void aBranchNameFindsItsLetterheadIgnoringCase() {
        Row a = row("Main", null, true, true), b = row("North", "Northside", false, true);
        assertSame(b, LetterheadRules.pick(List.of(a, b), null, " northSIDE "));
    }
    @Test void otherwiseThePracticeDefaultIsUsed() {
        Row a = row("Main", null, true, true), b = row("North", "Northside", false, true);
        assertSame(a, LetterheadRules.pick(List.of(a, b), null, null));
        assertSame(a, LetterheadRules.pick(List.of(a, b), null, "Unknown branch"));
        assertSame(a, LetterheadRules.pick(List.of(a, b), UUID.randomUUID(), null));
    }
    @Test void aRemovedLetterheadIsNeverUsed() {
        Row a = row("Main", null, true, false), b = row("North", "Northside", false, false);
        assertNull(LetterheadRules.pick(List.of(a, b), a.id(), "Northside"));
        assertNull(LetterheadRules.pick(List.of(), null, null));
        assertNull(LetterheadRules.pick(null, null, null));
    }
    @Test void aBranchLetterheadPrintsThePracticeAndTheBranch() {
        assertEquals("Sample Practice – Northside", LetterheadRules.toLetterhead(row("N", "Northside", false, true), null).name());
        assertEquals("Sample Practice", LetterheadRules.toLetterhead(row("M", null, true, true), null).name());
        assertNull(LetterheadRules.toLetterhead(null, null));
        var already = new Row(UUID.randomUUID(), "N", "Northside", "Sample Practice Northside", List.of(), false, true);
        assertEquals("Sample Practice Northside", LetterheadRules.toLetterhead(already, null).name());
    }
    @Test void aLetterheadNeedsANameAndAPracticeName() {
        assertTrue(LetterheadRules.problems("Main", "Practice", List.of("a"), null).isEmpty());
        assertEquals(2, LetterheadRules.problems(" ", null, List.of(), null).size());
        assertFalse(LetterheadRules.problems("Main", "Practice", List.of("1","2","3","4","5","6","7"), null).isEmpty());
        assertFalse(LetterheadRules.problems("Main", "Practice", List.of("x".repeat(121)), null).isEmpty());
    }
    @Test void aLogoIsCheckedByItsBytesNotItsName() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 1};
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 1};
        assertEquals("image/png", LetterheadRules.imageType(png));
        assertEquals("image/jpeg", LetterheadRules.imageType(jpg));
        assertNull(LetterheadRules.imageType(gif));
        assertNull(LetterheadRules.imageType(new byte[3]));
        assertTrue(LetterheadRules.problems("M", "P", List.of(), png).isEmpty());
        assertFalse(LetterheadRules.problems("M", "P", List.of(), gif).isEmpty());
        assertFalse(LetterheadRules.problems("M", "P", List.of(), new byte[LetterheadRules.LOGO_MAX_BYTES + 1]).isEmpty());
    }
    @Test void addressLinesAreTrimmedAndBlanksDropped() {
        assertEquals(List.of("a", "b"), LetterheadRules.tidy(java.util.Arrays.asList(" a ", "", null, "  ", "b")));
        assertEquals(List.of(), LetterheadRules.tidy(null));
    }
}
