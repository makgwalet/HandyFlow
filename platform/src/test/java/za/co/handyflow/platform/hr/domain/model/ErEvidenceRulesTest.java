package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.handyflow.platform.hr.domain.model.ErEvidenceRules.*;

class ErEvidenceRulesTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};
    private static final byte[] HTML = "<html><script>alert(1)</script>".getBytes();

    private static String check(String status, String kind, String name, long size, byte[] head, int active) {
        return uploadProblem(status, kind, name, size, head, active);
    }

    @Test
    void aCompleteFileIsAccepted() {
        assertThat(check(ErRules.INVESTIGATING, "STATEMENT", "witness.pdf", 1000, PDF, 0)).isNull();
        assertThat(check(ErRules.INVESTIGATING, "PHOTO", "Scene.PNG", 1000, PNG, 3)).isNull();
    }

    @Test
    void aClosedOrWithdrawnCaseTakesNoMoreFiles() {
        assertThat(check(ErRules.CLOSED, "STATEMENT", "a.pdf", 1000, PDF, 0)).contains("closed");
        assertThat(check(ErRules.WITHDRAWN, "STATEMENT", "a.pdf", 1000, PDF, 0)).contains("closed");
        assertThat(removeProblem(ErRules.CLOSED, "Wrong case")).contains("closed");
    }

    @Test
    void theKindSizeNameAndCountAreChecked() {
        assertThat(check(ErRules.REPORTED, null, "a.pdf", 1000, PDF, 0)).contains("kind");
        assertThat(check(ErRules.REPORTED, "GOSSIP", "a.pdf", 1000, PDF, 0)).contains("kind");
        assertThat(check(ErRules.REPORTED, "EMAIL", "", 1000, PDF, 0)).contains("no name");
        assertThat(check(ErRules.REPORTED, "EMAIL", "a.pdf", 0, PDF, 0)).contains("empty");
        assertThat(check(ErRules.REPORTED, "EMAIL", "a.pdf", MAX_BYTES + 1, PDF, 0)).contains("too large");
        assertThat(check(ErRules.REPORTED, "EMAIL", "a.pdf", MAX_BYTES, PDF, 0)).isNull();
        assertThat(check(ErRules.REPORTED, "EMAIL", "a.pdf", 1000, PDF, MAX_PER_CASE)).contains("at most 50");
    }

    @Test
    void pagesScriptsAndProgramsAreRefusedWhateverTheyAreCalled() {
        for (String name : new String[]{"x.html", "x.htm", "x.svg", "x.js", "x.exe", "x.bat", "x.xml", "x"})
            assertThat(check(ErRules.REPORTED, "DOCUMENT", name, 100, PDF, 0)).contains("not accepted");
        assertThat(check(ErRules.REPORTED, "DOCUMENT", "statement.pdf", 100, HTML, 0)).contains("does not look like");
        assertThat(check(ErRules.REPORTED, "DOCUMENT", "photo.png", 100, PDF, 0)).contains("does not look like");
    }

    @Test
    void theStoredTypeComesFromTheExtensionNotFromTheBrowser() {
        assertThat(contentType("a.PDF")).isEqualTo("application/pdf");
        assertThat(contentType("a.jpeg")).isEqualTo("image/jpeg");
        assertThat(contentType("a.html")).isNull();
        assertThat(extension("archive.tar.gz")).isEqualTo("gz");
        assertThat(extension("noext")).isEmpty();
        assertThat(extension("trailing.")).isEmpty();
    }

    @Test
    void eachExtensionHasItsOwnSignature() {
        assertThat(looksLike("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isTrue();
        assertThat(looksLike("docx", new byte[]{'P', 'K', 3, 4})).isTrue();
        assertThat(looksLike("xlsx", new byte[]{'P', 'K', 3, 4})).isTrue();
        assertThat(looksLike("wav", "RIFF....WAVE".getBytes())).isTrue();
        assertThat(looksLike("m4a", new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p'})).isTrue();
        assertThat(looksLike("mp3", "ID3".getBytes())).isTrue();
        assertThat(looksLike("mp3", new byte[]{(byte) 0xFF, (byte) 0xFB, 0})).isTrue();
        assertThat(looksLike("txt", "plain words".getBytes())).isTrue();
        assertThat(looksLike("txt", new byte[]{'a', 0, 'b'})).isFalse();
        assertThat(looksLike("pdf", new byte[0])).isFalse();
        assertThat(looksLike("pdf", null)).isFalse();
        assertThat(looksLike("exe", PDF)).isFalse();
    }

    @Test
    void aNameLosesItsFolderAndOddCharactersAndKeepsItsExtensionWhenCut() {
        assertThat(safeName("C:\\Users\\hr\\evidence\\Statement.pdf")).isEqualTo("Statement.pdf");
        assertThat(safeName("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(safeName("a\"b<c>.pdf")).isEqualTo("abc.pdf");
        String cut = safeName("x".repeat(300) + ".pdf");
        assertThat(cut.length()).isEqualTo(MAX_NAME);
        assertThat(cut).endsWith(".pdf");
    }

    @Test
    void aRemovalNeedsAReasonOfSensibleLength() {
        assertThat(removeProblem(ErRules.INVESTIGATING, " ")).contains("reason");
        assertThat(removeProblem(ErRules.INVESTIGATING, "x".repeat(301))).contains("too long");
        assertThat(removeProblem(ErRules.INVESTIGATING, "Uploaded to the wrong case")).isNull();
    }

    @Test
    void theTimelineNotesNameTheFileAndTheKindButNothingFromInsideIt() {
        assertThat(addedNote("RECORDING", "dir/call.m4a")).isEqualTo("Recording – call.m4a");
        assertThat(removedNote("call.m4a", " Wrong case ")).isEqualTo("call.m4a – Wrong case");
    }
}
