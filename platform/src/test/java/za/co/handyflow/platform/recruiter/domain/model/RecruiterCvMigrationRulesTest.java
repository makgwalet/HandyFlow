package za.co.handyflow.platform.recruiter.domain.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterCvMigrationRulesTest {
    private static final String PDF = Base64.getEncoder().encodeToString("%PDF-1.4 a small file".getBytes(StandardCharsets.US_ASCII));

    @Test
    void aStoredFileReferenceIsNotTheOldKind() {
        assertThat(RecruiterCvMigrationRules.isLegacy("local://abc.pdf")).isFalse();
        assertThat(RecruiterCvMigrationRules.isLegacy("s3://bucket/key.pdf")).isFalse();
        assertThat(RecruiterCvMigrationRules.isLegacy(null)).isFalse();
        assertThat(RecruiterCvMigrationRules.isLegacy("  ")).isFalse();
        assertThat(RecruiterCvMigrationRules.isLegacy(PDF)).isTrue();
    }

    @Test
    void oldValuesAreDecodedWhenTheyAreAPdf() {
        assertThat(RecruiterCvMigrationRules.legacyBytes(PDF)).startsWith('%', 'P', 'D', 'F');
        assertThat(RecruiterCvMigrationRules.legacyBytes("data:application/pdf;base64," + PDF)).isNotNull();
        assertThat(RecruiterCvMigrationRules.legacyBytes(PDF.substring(0, 12) + "\r\n" + PDF.substring(12))).isNotNull();
    }

    @Test
    void anythingElseIsLeftAlone() {
        assertThat(RecruiterCvMigrationRules.legacyBytes("not base64 !!")).isNull();
        assertThat(RecruiterCvMigrationRules.legacyBytes(Base64.getEncoder().encodeToString("plain text".getBytes(StandardCharsets.US_ASCII)))).isNull();
        assertThat(RecruiterCvMigrationRules.legacyBytes("local://abc.pdf")).isNull();
        assertThat(RecruiterCvMigrationRules.legacyBytes(null)).isNull();
    }

    @Test
    void fileNamesAndBatches() {
        assertThat(RecruiterCvMigrationRules.fileName("Thandi CV.pdf")).isEqualTo("Thandi CV.pdf");
        assertThat(RecruiterCvMigrationRules.fileName("../x.pdf")).isEqualTo("cv.pdf");
        assertThat(RecruiterCvMigrationRules.fileName("resume.docx")).isEqualTo("cv.pdf");
        assertThat(RecruiterCvMigrationRules.fileName(null)).isEqualTo("cv.pdf");
        assertThat(RecruiterCvMigrationRules.batchOrDefault(null)).isEqualTo(25);
        assertThat(RecruiterCvMigrationRules.batchOrDefault(0)).isEqualTo(1);
        assertThat(RecruiterCvMigrationRules.batchOrDefault(5000)).isEqualTo(100);
    }
}
