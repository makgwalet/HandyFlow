package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MediaRulesTest {

    @Test
    void picturesAndPdfsAreAcceptedAndNothingElse() {
        assertThat(MediaRules.kindOf("image/png")).isEqualTo(MediaRules.Kind.IMAGE);
        assertThat(MediaRules.kindOf("IMAGE/JPEG ")).isEqualTo(MediaRules.Kind.IMAGE);
        assertThat(MediaRules.kindOf("application/pdf")).isEqualTo(MediaRules.Kind.DOCUMENT);
        assertThatThrownBy(() -> MediaRules.kindOf("image/svg+xml")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaRules.kindOf("text/html")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaRules.kindOf(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentMustMatchTheDeclaredType() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        byte[] exe = {'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(MediaRules.signatureMatches("image/png", png, 12)).isTrue();
        assertThat(MediaRules.signatureMatches("application/pdf", pdf, 12)).isTrue();
        assertThat(MediaRules.signatureMatches("image/webp", webp, 12)).isTrue();
        assertThat(MediaRules.signatureMatches("image/png", exe, 12)).isFalse();
        assertThat(MediaRules.signatureMatches("image/png", pdf, 12)).isFalse();
        assertThat(MediaRules.signatureMatches("image/webp", png, 12)).isFalse();
        assertThat(MediaRules.signatureMatches("image/png", png, 3)).isFalse();
    }

    @Test
    void fileNamesLoseTheirPathAndOddCharacters() {
        assertThat(MediaRules.safeFileName("C:\\temp\\..\\photo 1.png")).isEqualTo("photo 1.png");
        assertThat(MediaRules.safeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(MediaRules.safeFileName("a<b>.pdf")).isEqualTo("a_b_.pdf");
        assertThat(MediaRules.safeFileName("  ")).isEqualTo("file");
        assertThat(MediaRules.safeFileName(null)).isEqualTo("file");
    }

    @Test
    void textIsTrimmedAndLimited() {
        assertThat(MediaRules.text("  Front  ", 10, "The title")).isEqualTo("Front");
        assertThat(MediaRules.text("   ", 10, "The title")).isNull();
        assertThatThrownBy(() -> MediaRules.text("x".repeat(11), 10, "The title")).hasMessageContaining("at most 10");
    }

    @Test
    void onlyAPictureCanBeTheFirstMainPicture() {
        assertThat(MediaRules.becomesPrimary(MediaRules.Kind.IMAGE, false)).isTrue();
        assertThat(MediaRules.becomesPrimary(MediaRules.Kind.IMAGE, true)).isFalse();
        assertThat(MediaRules.becomesPrimary(MediaRules.Kind.DOCUMENT, false)).isFalse();
    }
}
